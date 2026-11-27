package com.duskafka.studybot.judgment;

import static com.duskafka.studybot.progress.StudyDayRows.SEOUL;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.progress.StudyDayRows;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 판정 배치가 두 인스턴스에서 동시에 떠도 한 번만 돈다 (S12 만들 것 1·수용 기준, ShedLock JDBC). 서버 두 대 대신 같은 진입점을 두 스레드에서
 * 부른다. 잠금은 DB의 {@code shedlock} 행이라 서버가 하나든 둘이든 같다.
 *
 * <p>테스트가 이 테스트의 스터디 행을 잡아 첫 실행을 그 스터디의 기수 준비에서 멈춰 둔다. 다른 테스트가 남긴 스터디(2026년)는 아무것도 할 게
 * 없도록 2025년 날짜로 본다.
 */
@IntegrationTest
class JudgmentScheduleLockIntegrationTest {

	private static final LocalDate MAR_1 = LocalDate.of(2025, 3, 1);

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JudgmentSchedule schedule;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private StudyDayRows rows;

	private JudgmentRows judgments;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		judgments = new JudgmentRows(jdbc);
		deleteLock();
	}

	@AfterEach
	void tearDown() {
		deleteLock();
	}

	@Test
	@DisplayName("판정 배치가 도는 동안 다른 인스턴스의 실행은 판정하지 않고 바로 끝난다")
	void secondRunIsSkippedWhileFirstHoldsTheLock() throws Exception {
		long alice = rows.newUser("s12-shedlock");
		long studyId = rows.newStudy(SEOUL);
		rows.addRule(studyId, MAR_1, 1, LocalTime.MIDNIGHT, 30, "");
		judgments.insertSeason(studyId, 1, MAR_1, LocalDate.of(2025, 3, 28), "ACTIVE");
		rows.join(studyId, alice, MAR_1);
		judgments.setLastJudgedDay(studyId, LocalDate.of(2025, 3, 8));
		clock.setInstant(seoul(LocalDate.of(2025, 3, 10), 0, 40));

		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		CountDownLatch held = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
				jdbc.sql("select id from studybot.studies where id = :id for update")
					.param("id", studyId)
					.query(Long.class)
					.single();
				held.countDown();
				awaitRelease(release);
			}));
			assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
			Future<?> first = executor.submit(schedule::runScheduled);
			await().atMost(Duration.ofSeconds(30)).until(() -> waitersOn("studies") >= 1);

			schedule.runScheduled();

			assertThat(first).as("첫 실행은 아직 스터디 행을 기다린다").isNotDone();
			assertThat(waitersOn("studies")).as("둘째 실행은 기다리지 않고 끝났다").isEqualTo(1);
			assertThat(judgments.lastJudgedDay(studyId)).isEqualTo(LocalDate.of(2025, 3, 8));
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);
			first.get(30, TimeUnit.SECONDS);
		}
		finally {
			release.countDown();
			executor.shutdownNow();
		}
		assertThat(judgments.lastJudgedDay(studyId)).as("첫 실행만 판정했다").isEqualTo(LocalDate.of(2025, 3, 9));
	}

	private void deleteLock() {
		jdbc.sql("delete from studybot.shedlock").update();
	}

	private long waitersOn(String table) {
		return jdbc.sql("""
				select count(*) from pg_stat_activity
				where datname = current_database() and pid <> pg_backend_pid()
					and wait_event_type = 'Lock' and query ilike :pattern
				""").param("pattern", "%" + table + "%").query(Long.class).single();
	}

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

}
