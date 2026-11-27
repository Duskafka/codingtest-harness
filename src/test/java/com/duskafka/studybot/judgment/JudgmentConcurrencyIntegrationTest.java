package com.duskafka.studybot.judgment;

import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.progress.SolveToAssign;
import com.duskafka.studybot.progress.StudyDayAssignment;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * T8: 유예가 끝나기 직전에 들어온 풀이와 판정 배치가 겹쳐도 결과가 둘 중 하나로 일관된다 (S12 수용 기준, 5.10 '늦게 온 풀이와 경합').
 *
 * <p>배정(S09)과 판정은 같은 멤버 일 행을 잠근다 (결정 제안서 007). 테스트가 먼저 그 행을 잡아 두고, 둘을 정한 순서로 기다리게 한 뒤 놓아
 * 준다. PostgreSQL은 같은 행을 기다리는 트랜잭션을 기다린 순서대로 깨운다.
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 1기 11-01~11-28. 풀이는 11-21 00:29(유예 안)에 받았고 11-20은 목표
 * 미달이라, 판정 전이면 11-20으로 간다 (R4). 11-20의 판정 시각은 11-21 00:40이다.
 */
@IntegrationTest
class JudgmentConcurrencyIntegrationTest {

	private static final LocalDate NOV_19 = LocalDate.of(2026, 11, 19);

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private static final Instant LATE_SOLVE = seoul(NOV_21, 0, 29);

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private DailyJudgmentBatch batch;

	@Autowired
	private StudyDayAssignment assignment;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private StudyDayRows rows;

	private JudgmentRows judgments;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		judgments = new JudgmentRows(jdbc);
	}

	@Test
	@DisplayName("T8: 배정이 먼저 행을 잡으면 판정은 그 풀이를 세어 성공으로 판정한다")
	void t8AssignmentFirstThenJudgmentSeesTheSolve() throws Exception {
		Fixture fixture = fixture("s12-t8-assign");

		race(fixture, true);

		assertThat(judgments.result(fixture.study.studyId(), fixture.userId, NOV_20)).isEqualTo("SUCCESS");
		assertThat(rows.solvedCount(fixture.study.studyId(), fixture.userId, NOV_20)).isEqualTo(1);
		assertThat(rows.studyDaySolves(fixture.study.studyId(), fixture.userId)).as("누락·중복 없음")
			.extracting(StudyDayRows.StudyDaySolveRow::studyDay)
			.containsExactly(NOV_20);
	}

	@Test
	@DisplayName("T8: 판정이 먼저 행을 잡으면 실패로 판정하고, 배정은 판정된 전날 대신 오늘로 보낸다")
	void t8JudgmentFirstThenAssignmentGoesToToday() throws Exception {
		Fixture fixture = fixture("s12-t8-judge");

		race(fixture, false);

		assertThat(judgments.result(fixture.study.studyId(), fixture.userId, NOV_20)).isEqualTo("FAIL");
		assertThat(rows.solvedCount(fixture.study.studyId(), fixture.userId, NOV_20)).isZero();
		assertThat(rows.studyDaySolves(fixture.study.studyId(), fixture.userId)).as("누락·중복 없음")
			.extracting(StudyDayRows.StudyDaySolveRow::studyDay)
			.containsExactly(NOV_21);
	}

	private Fixture fixture(String login) {
		long userId = rows.newUser(login);
		TestStudy study = rows.standardStudy(userId);
		rows.putMemberDay(study.studyId(), userId, NOV_20, 0, null, null);
		judgments.setLastJudgedDay(study.studyId(), NOV_19);
		long solveId = rows.solve(userId, "BOJ", "1000", LATE_SOLVE);
		clock.setInstant(seoul(NOV_21, 0, 40));
		return new Fixture(userId, study, solveId);
	}

	/**
	 * 테스트가 11-20 멤버 일 행을 잡은 채 두 작업을 정한 순서로 기다리게 하고 놓아 준다.
	 *
	 * @param assignmentFirst 배정이 먼저 기다리는지
	 */
	private void race(Fixture fixture, boolean assignmentFirst) throws Exception {
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		CountDownLatch held = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(3);
		try {
			Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
				jdbc.sql("""
						select id from studybot.member_days
						where study_id = :studyId and user_id = :userId and study_day = :day for update
						""")
					.param("studyId", fixture.study.studyId())
					.param("userId", fixture.userId)
					.param("day", NOV_20)
					.query(Long.class)
					.single();
				held.countDown();
				awaitRelease(release);
			}));
			assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
			Runnable assign = () -> transaction.executeWithoutResult(status -> assignment
				.assign(new SolveToAssign(fixture.solveId, fixture.userId, "BOJ", "1000", LATE_SOLVE)));
			Runnable judge = () -> batch.judgeStudy(fixture.study.studyId());

			Future<?> first = executor.submit(assignmentFirst ? assign : judge);
			await().atMost(Duration.ofSeconds(10)).until(() -> waitersOn("member_days") >= 1);
			Future<?> second = executor.submit(assignmentFirst ? judge : assign);
			await().atMost(Duration.ofSeconds(10)).until(() -> waitersOn("member_days") >= 2);
			release.countDown();

			holder.get(10, TimeUnit.SECONDS);
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);
		}
		finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	/** 그 테이블의 행 잠금을 기다리는 다른 연결 수 (pg_stat_activity). */
	private long waitersOn(String table) {
		return jdbc.sql("""
				select count(*) from pg_stat_activity
				where datname = current_database() and pid <> pg_backend_pid()
					and wait_event_type = 'Lock' and query ilike :pattern
				""").param("pattern", "%" + table + "%").query(Long.class).single();
	}

	/**
	 * 테스트가 놓아 줄 때까지 기다린다. 제한 시간 안에 놓아 주지 않거나 인터럽트되면 예외를 던져 잡은 트랜잭션이 롤백되게 한다
	 * ({@code ExemptionIntegrationTest}와 같음).
	 */
	private static void awaitRelease(CountDownLatch latch) {
		try {
			if (!latch.await(60, TimeUnit.SECONDS)) {
				throw new IllegalStateException("테스트가 60초 안에 놓아 주지 않았어요");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("놓아 주기를 기다리다 인터럽트됐어요", ex);
		}
	}

	private record Fixture(long userId, TestStudy study, long solveId) {
	}

}
