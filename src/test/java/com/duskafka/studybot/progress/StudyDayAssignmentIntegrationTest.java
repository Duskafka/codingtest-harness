package com.duskafka.studybot.progress;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.NOV_28;
import static com.duskafka.studybot.progress.StudyDayRows.SEOUL;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.progress.StudyDayRows.StudyDaySolveRow;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스터디 일 배정의 통합 테스트 (S09 수용 기준, R4·R5·R9·R14, 5.5). 실제 PostgreSQL에서 멤버 일 행을 만들고 잠그며 센다.
 *
 * <p>사용자·스터디·규칙 버전·기수·멤버십·풀이 기록은 {@link StudyDayRows}로 바로 넣고, 배정 연결 지점({@link StudyDayAssignment})을
 * 풀이 기록을 넣는 쪽처럼 트랜잭션 안에서 부른다. push 웹훅을 거치는 경우는 {@code github.PushStudyDayAssignmentIntegrationTest}가 본다.
 *
 * <p>S09 수용 기준의 기본값: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 1기 11-01~11-28. 테스트마다 사용자와 스터디를 새로 만든다.
 */
@IntegrationTest
class StudyDayAssignmentIntegrationTest {

	private static final LocalDate NOV_19 = LocalDate.of(2026, 11, 19);

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	/** 11-21 00:05 (서울). 11-21의 유예 안. */
	private static final Instant NOV_21_0005 = seoul(NOV_21, 0, 5);

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private StudyDayAssignment assignment;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private StudyDayRows rows;

	private TransactionTemplate transaction;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		transaction = new TransactionTemplate(transactionManager);
	}

	/** 풀이 기록을 넣고, 풀이 기록을 넣는 쪽처럼 트랜잭션 안에서 배정한다. 풀이 기록 ID를 돌려준다. */
	private long solveAndAssign(long userId, String problemNo, Instant receivedAt) {
		long solveId = rows.solve(userId, "BOJ", problemNo, receivedAt);
		transaction.executeWithoutResult(status -> assignment.assign(toAssign(solveId, userId, problemNo, receivedAt)));
		return solveId;
	}

	private static SolveToAssign toAssign(long solveId, long userId, String problemNo, Instant receivedAt) {
		return new SolveToAssign(solveId, userId, "BOJ", problemNo, receivedAt);
	}

	// --- T6와 R4의 조건 ---

	@Test
	@DisplayName("T6: 00:05 수신, 전날 미달 → 전날로 배정 (전날 1문제, 오늘 0문제)")
	void t6PreviousDayBelowGoal() {
		long user = rows.newUser("t6-below");
		TestStudy study = rows.standardStudy(user);

		long solveId = solveAndAssign(user, "1000", NOV_21_0005);

		assertThat(rows.studyDaySolves(study.studyId(), user))
			.containsExactly(new StudyDaySolveRow(NOV_20, solveId, "BOJ", "1000"));
		assertThat(rows.solvedCount(study.studyId(), user, NOV_20)).isEqualTo(1);
		assertThat(rows.solvedCount(study.studyId(), user, NOV_21)).isZero();
	}

	@Test
	@DisplayName("T6: 00:05 수신, 전날 이미 달성 → 오늘로 배정")
	void t6PreviousDayAlreadyAtGoal() {
		long user = rows.newUser("t6-met");
		TestStudy study = rows.standardStudy(user);
		solveAndAssign(user, "1000", seoul(NOV_20, 14, 0));

		solveAndAssign(user, "1001", NOV_21_0005);

		assertThat(rows.studyDaySolves(study.studyId(), user)).extracting(StudyDaySolveRow::studyDay, StudyDaySolveRow::problemNo)
			.containsExactly(tuple(NOV_20, "1000"),
					tuple(NOV_21, "1001"));
		assertThat(rows.solvedCount(study.studyId(), user, NOV_20)).isEqualTo(1);
		assertThat(rows.solvedCount(study.studyId(), user, NOV_21)).isEqualTo(1);
	}

	@Test
	@DisplayName("S09: 유예 밖(00:35) 수신 → 전날 미달이어도 오늘로 배정")
	void outsideGraceGoesToToday() {
		long user = rows.newUser("after-grace");
		TestStudy study = rows.standardStudy(user);

		solveAndAssign(user, "1000", seoul(NOV_21, 0, 35));

		assertThat(rows.solvedCount(study.studyId(), user, NOV_20)).isZero();
		assertThat(rows.solvedCount(study.studyId(), user, NOV_21)).isEqualTo(1);
	}

	@Test
	@DisplayName("S09: 전날이 이미 판정됐으면 유예 안이어도 오늘로 배정 (전날 결과·센 수는 그대로)")
	void judgedPreviousDayGoesToToday() {
		long user = rows.newUser("judged");
		TestStudy study = rows.standardStudy(user);
		rows.putMemberDay(study.studyId(), user, NOV_20, 0, "FAIL", null);

		solveAndAssign(user, "1000", NOV_21_0005);

		assertThat(rows.memberDay(study.studyId(), user, NOV_20)).hasValueSatisfying(day -> {
			assertThat(day.solvedCount()).isZero();
			assertThat(day.result()).isEqualTo("FAIL");
		});
		assertThat(rows.solvedCount(study.studyId(), user, NOV_21)).isEqualTo(1);
	}

	@Test
	@DisplayName("S09: 전날에 면제권을 썼으면 오늘로 배정")
	void exemptionOnPreviousDayGoesToToday() {
		long user = rows.newUser("exempted");
		TestStudy study = rows.standardStudy(user);
		rows.putMemberDay(study.studyId(), user, NOV_20, 0, null, seoul(NOV_20, 9, 0));

		solveAndAssign(user, "1000", NOV_21_0005);

		assertThat(rows.solvedCount(study.studyId(), user, NOV_20)).isZero();
		assertThat(rows.solvedCount(study.studyId(), user, NOV_21)).isEqualTo(1);
	}

	@Test
	@DisplayName("S09: 전날(금요일)이 쉬는 요일이면 오늘로 배정")
	void restDayPreviousGoesToToday() {
		long user = rows.newUser("rest-day");
		TestStudy study = rows.standardStudy(user, SEOUL, LocalTime.MIDNIGHT, 30, "FRIDAY");

		solveAndAssign(user, "1000", NOV_21_0005);

		assertThat(rows.solvedCount(study.studyId(), user, NOV_20)).isZero();
		assertThat(rows.memberDay(study.studyId(), user, NOV_20)).as("후보가 아닌 전날 행은 만들지 않는다").isEmpty();
		assertThat(rows.solvedCount(study.studyId(), user, NOV_21)).isEqualTo(1);
	}

	@Test
	@DisplayName("S09: 전날의 목표는 전날 규칙 버전으로 본다 (전날 목표 2에 1문제 → 00:05 풀이는 전날로)")
	void previousGoalFromPreviousVersion() {
		long user = rows.newUser("goal-version");
		long studyId = rows.newStudy(SEOUL);
		rows.addRule(studyId, NOV_1, 2, LocalTime.MIDNIGHT, 30, "");
		rows.addRule(studyId, NOV_21, 1, LocalTime.MIDNIGHT, 30, "");
		rows.addSeason(studyId, 1, NOV_1, NOV_28);
		rows.join(studyId, user, NOV_1);
		solveAndAssign(user, "1000", seoul(NOV_20, 14, 0));

		solveAndAssign(user, "1001", NOV_21_0005);

		assertThat(rows.solvedCount(studyId, user, NOV_20)).isEqualTo(2);
		assertThat(rows.solvedCount(studyId, user, NOV_21)).isZero();
	}

	// --- T25: 기준 시각과 시간대 ---

	@Test
	@DisplayName("T25: 기준 06:00 스터디의 새벽 03:00 풀이는 전날(11-20)로 집계된다 (11-19가 미달이어도 유예와 상관없음)")
	void t25DayBoundarySix() {
		long user = rows.newUser("boundary-six");
		TestStudy study = rows.standardStudy(user, SEOUL, LocalTime.of(6, 0), 30, "");

		solveAndAssign(user, "1000", seoul(NOV_21, 3, 0));

		assertThat(rows.studyDaySolves(study.studyId(), user)).extracting(StudyDaySolveRow::studyDay)
			.containsExactly(NOV_20);
		assertThat(rows.solvedCount(study.studyId(), user, NOV_19)).isZero();
	}

	@Test
	@DisplayName("T25: 서울 스터디와 뉴욕 스터디에 속한 사용자의 같은 풀이는 각 시간대로 배정된다 (서울 14:05 → 11-21, 뉴욕 00:05 → 전날 11-20)")
	void t25EachStudyUsesItsTimeZone() {
		long user = rows.newUser("two-zones");
		TestStudy seoulStudy = rows.standardStudy(user);
		TestStudy newYorkStudy = rows.standardStudy(user, ZoneId.of("America/New_York"), LocalTime.MIDNIGHT, 30, "");

		solveAndAssign(user, "1000", Instant.parse("2026-11-21T05:05:00Z"));

		assertThat(rows.studyDaySolves(seoulStudy.studyId(), user)).extracting(StudyDaySolveRow::studyDay)
			.containsExactly(NOV_21);
		assertThat(rows.studyDaySolves(newYorkStudy.studyId(), user)).extracting(StudyDaySolveRow::studyDay)
			.containsExactly(NOV_20);
	}

	// --- 판정 대상 (가입, 기수, 탈퇴) ---

	@Test
	@DisplayName("S09: 가입 다음 날 00:05의 풀이는 가입한 날(판정 대상 아님, R14)로 배정되지 않고 오늘로 배정된다")
	void dayAfterJoiningGoesToToday() {
		long user = rows.newUser("new-member");
		long studyId = rows.newStudy(SEOUL);
		rows.addRule(studyId, NOV_1, 1, LocalTime.MIDNIGHT, 30, "");
		rows.addSeason(studyId, 1, NOV_1, NOV_28);
		rows.join(studyId, user, NOV_21);

		solveAndAssign(user, "1000", NOV_21_0005);

		assertThat(rows.studyDaySolves(studyId, user)).extracting(StudyDaySolveRow::studyDay).containsExactly(NOV_21);
	}

	@Test
	@DisplayName("S09: 1기 시작일 00:05의 풀이는 기수 밖인 전날(미달)로 가지 않고 오늘로. 오늘 행에는 기수 행 ID가, 1기 전 날의 행에는 빈 기수가 들어간다")
	void dayBeforeFirstSeasonIsNotJudged() {
		long user = rows.newUser("pre-season");
		long studyId = rows.newStudy(SEOUL);
		rows.addRule(studyId, NOV_21, 1, LocalTime.MIDNIGHT, 30, "");
		long seasonId = rows.addSeason(studyId, 1, NOV_21, LocalDate.of(2026, 12, 18));
		rows.join(studyId, user, NOV_1);
		solveAndAssign(user, "999", seoul(NOV_19, 15, 0));

		solveAndAssign(user, "1000", NOV_21_0005);

		assertThat(rows.memberDay(studyId, user, NOV_19)).hasValueSatisfying(day -> {
			assertThat(day.seasonId()).isNull();
			assertThat(day.solvedCount()).isEqualTo(1);
		});
		assertThat(rows.memberDay(studyId, user, NOV_20)).as("후보가 아닌 전날 행은 만들지 않는다").isEmpty();
		assertThat(rows.memberDay(studyId, user, NOV_21)).hasValueSatisfying(day -> {
			assertThat(day.seasonId()).isEqualTo(seasonId);
			assertThat(day.solvedCount()).isEqualTo(1);
		});
	}

	@Test
	@DisplayName("S09: 스터디 2개에 속한 사용자의 풀이 하나가 두 스터디에 각각 반영된다")
	void oneSolveCountsInEveryStudy() {
		long user = rows.newUser("two-studies");
		TestStudy first = rows.standardStudy(user);
		TestStudy second = rows.standardStudy(user);

		long solveId = solveAndAssign(user, "1000", seoul(NOV_21, 9, 0));

		assertThat(rows.studyDaySolves(first.studyId(), user))
			.containsExactly(new StudyDaySolveRow(NOV_21, solveId, "BOJ", "1000"));
		assertThat(rows.studyDaySolves(second.studyId(), user))
			.containsExactly(new StudyDaySolveRow(NOV_21, solveId, "BOJ", "1000"));
		assertThat(rows.memberDay(first.studyId(), user, NOV_21)).hasValueSatisfying(day -> {
			assertThat(day.solvedCount()).isEqualTo(1);
			assertThat(day.seasonId()).isEqualTo(first.seasonId());
		});
		assertThat(rows.memberDay(second.studyId(), user, NOV_21)).hasValueSatisfying(day -> {
			assertThat(day.solvedCount()).isEqualTo(1);
			assertThat(day.seasonId()).isEqualTo(second.seasonId());
		});
	}

	@Test
	@DisplayName("S09: 탈퇴한 스터디에는 탈퇴한 날의 풀이도, 유예로 탈퇴한 날에 들어올 풀이도 반영하지 않는다 (사람이 정함 \"활성 멤버십만\")")
	void leftStudyGetsNothing() {
		long user = rows.newUser("leaver");
		TestStudy stayed = rows.standardStudy(user);
		TestStudy left = rows.standardStudy(user);
		rows.leave(left.membershipId(), seoul(NOV_20, 10, 0));

		solveAndAssign(user, "1000", seoul(NOV_20, 23, 0));
		solveAndAssign(user, "1001", NOV_21_0005);

		assertThat(rows.studyDaySolves(left.studyId(), user)).isEmpty();
		assertThat(rows.memberDay(left.studyId(), user, NOV_20)).isEmpty();
		assertThat(rows.memberDay(left.studyId(), user, NOV_21)).isEmpty();
		assertThat(rows.studyDaySolves(stayed.studyId(), user)).extracting(StudyDaySolveRow::studyDay)
			.containsExactly(NOV_20, NOV_21);
	}

	// --- T4: 같은 날 같은 문제 ---

	@Test
	@DisplayName("T4: 같은 날 같은 문제의 풀이 기록이 둘이어도 1문제로 센다 (먼저 배정한 기록만 남음). 다른 문제는 따로 센다")
	void t4SameProblemSameDayCountsOnce() {
		long user = rows.newUser("t4");
		TestStudy study = rows.standardStudy(user);

		long first = solveAndAssign(user, "1000", seoul(NOV_21, 9, 0));
		solveAndAssign(user, "1000", seoul(NOV_21, 21, 0));
		solveAndAssign(user, "1001", seoul(NOV_21, 22, 0));

		assertThat(rows.studyDaySolves(study.studyId(), user)).extracting(StudyDaySolveRow::problemNo)
			.containsExactly("1000", "1001");
		assertThat(rows.studyDaySolves(study.studyId(), user).getFirst().solveRecordId()).isEqualTo(first);
		assertThat(rows.solvedCount(study.studyId(), user, NOV_21)).isEqualTo(2);
	}

	@Test
	@DisplayName("S09: 배정은 풀이 기록을 넣는 쪽의 트랜잭션 안에서만 부른다 (트랜잭션 없이 부르면 예외)")
	void assignRequiresTransaction() {
		long user = rows.newUser("no-tx");
		rows.standardStudy(user);
		long solveId = rows.solve(user, "BOJ", "1000", NOV_21_0005);

		assertThatThrownBy(() -> assignment.assign(toAssign(solveId, user, "1000", NOV_21_0005)))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	// --- T7: 동시성 ---

	@Test
	@DisplayName("T7: 00:05에 서로 다른 문제 풀이 2개가 동시에 배정되면 전날 1, 오늘 1. 20번 반복해도 같다")
	void t7ConcurrentSolvesInGraceSplitBetweenDays() throws Exception {
		for (int round = 0; round < 20; round++) {
			long user = rows.newUser("t7-" + round);
			TestStudy study = rows.standardStudy(user);
			long first = rows.solve(user, "BOJ", "1000", NOV_21_0005);
			long second = rows.solve(user, "BOJ", "1001", NOV_21_0005);

			runConcurrently(List.of(
					() -> inTransaction(List.of(toAssign(first, user, "1000", NOV_21_0005))),
					() -> inTransaction(List.of(toAssign(second, user, "1001", NOV_21_0005)))));

			assertThat(rows.solvedCount(study.studyId(), user, NOV_20)).as("round %d 전날", round).isEqualTo(1);
			assertThat(rows.solvedCount(study.studyId(), user, NOV_21)).as("round %d 오늘", round).isEqualTo(1);
		}
	}

	@Test
	@DisplayName("S09: 스터디 2개 사용자에게 유예 안 push(문제 2개)와 유예 밖 push가 동시에 와도 교착 없이 끝나고 수가 맞는다 (결정 제안서 007, 15번 반복)")
	void mixedConcurrentPushesAcrossTwoStudiesDoNotDeadlock() throws Exception {
		for (int round = 0; round < 15; round++) {
			long user = rows.newUser("no-deadlock-" + round);
			TestStudy belowGoal = rows.standardStudy(user);
			TestStudy atGoal = rows.standardStudy(user);
			rows.putMemberDay(atGoal.studyId(), user, NOV_20, 1, null, null);
			Instant afterGrace = seoul(NOV_21, 0, 40);
			long p1 = rows.solve(user, "BOJ", "1000", NOV_21_0005);
			long p2 = rows.solve(user, "BOJ", "1001", NOV_21_0005);
			long p3 = rows.solve(user, "BOJ", "1002", afterGrace);

			runConcurrently(List.of(
					() -> inTransaction(List.of(toAssign(p1, user, "1000", NOV_21_0005),
							toAssign(p2, user, "1001", NOV_21_0005))),
					() -> inTransaction(List.of(toAssign(p3, user, "1002", afterGrace)))));

			assertThat(rows.solvedCount(belowGoal.studyId(), user, NOV_20)).as("round %d", round).isEqualTo(1);
			assertThat(rows.solvedCount(belowGoal.studyId(), user, NOV_21)).as("round %d", round).isEqualTo(2);
			assertThat(rows.solvedCount(atGoal.studyId(), user, NOV_20)).as("round %d", round).isEqualTo(1);
			assertThat(rows.solvedCount(atGoal.studyId(), user, NOV_21)).as("round %d", round).isEqualTo(3);
		}
	}

	/** 한 트랜잭션에서 풀이들을 차례로 배정한다 (push 하나의 커밋들처럼). */
	private Void inTransaction(List<SolveToAssign> solves) {
		transaction.executeWithoutResult(status -> solves.forEach(assignment::assign));
		return null;
	}

	/** 작업들을 각자 스레드에서 동시에 시작하고 모두 끝날 때까지 기다린다. 하나라도 실패하면 그 예외로 실패한다. */
	private static void runConcurrently(List<Callable<Void>> tasks) throws Exception {
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			List<Future<Void>> futures = new ArrayList<>();
			for (Callable<Void> task : tasks) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					go.await();
					return task.call();
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			for (Future<Void> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

}
