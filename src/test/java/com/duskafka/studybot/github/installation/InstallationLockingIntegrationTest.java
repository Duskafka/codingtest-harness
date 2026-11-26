package com.duskafka.studybot.github.installation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.github.GithubConnectionRows;
import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import com.duskafka.studybot.github.webhook.HandlingResult;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 설치의 웹훅이 겹칠 때 상태를 덮어쓰지 않는지 본다 (CodeRabbit 리뷰: deleted·suspend·unsuspend가 잠금 없이 읽음).
 * 처리기는 설치 행을 잠그고 읽으므로, 먼저 시작한 처리가 커밋할 때까지 뒤의 처리가 기다렸다가 바뀐 상태를 본다.
 *
 * <p>deleted가 행을 쥔 채 멈춰 있는 동안 suspend를 보낸다. suspend가 DB 잠금을 기다리기 시작한 것을 확인한 뒤 deleted를 커밋한다
 * (시간이 아니라 PostgreSQL의 잠금 대기 상태로 맞춘다). 잠그고 읽으면 suspend는 커밋된 삭제를 보고 IGNORED다. 잠금 없이 읽으면
 * suspend는 이미 ACTIVE를 읽은 뒤 UPDATE에서 기다리다가 SUSPENDED로 덮어써서 PROCESSED가 된다.
 */
@IntegrationTest
class InstallationLockingIntegrationTest {

	@Autowired
	GithubInstallationLinker linker;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	MutableClock clock;

	@Test
	@DisplayName("S07: deleted가 설치를 쥔 동안 온 suspend는 기다렸다가 삭제를 보고 IGNORED (삭제를 덮어쓰지 않음)")
	void suspendWaitsForConcurrentDelete() throws Exception {
		GithubConnectionRows rows = new GithubConnectionRows(jdbc);
		TestUser user = rows.newUser("locking", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		CountDownLatch deleteApplied = new CountDownLatch(1);
		CountDownLatch commitDelete = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<HandlingResult> deleting = executor.submit(() -> new TransactionTemplate(this.transactionManager)
				.execute(status -> {
					HandlingResult result = this.linker.deleted(installationId);
					deleteApplied.countDown();
					awaitRelease(commitDelete);
					return result;
				}));
			assertThat(deleteApplied.await(10, TimeUnit.SECONDS)).as("deleted가 먼저 반영됨").isTrue();

			Future<HandlingResult> suspending = executor.submit(() -> this.linker.suspended(installationId));
			await().atMost(Duration.ofSeconds(10)).until(this::someoneWaitsForInstallationLock);
			commitDelete.countDown();

			assertThat(deleting.get(10, TimeUnit.SECONDS)).isEqualTo(HandlingResult.PROCESSED);
			assertThat(suspending.get(10, TimeUnit.SECONDS)).isEqualTo(HandlingResult.IGNORED);
			assertThat(rows.existingInstallation(installationId).status()).isEqualTo("DELETED");
		}
		finally {
			commitDelete.countDown();
			executor.shutdownNow();
		}
	}

	/** 다른 연결이 github_installations의 행 잠금을 기다리고 있는지 (pg_stat_activity). */
	private boolean someoneWaitsForInstallationLock() {
		return this.jdbc.sql("""
				select count(*) from pg_stat_activity
				where wait_event_type = 'Lock' and query ilike '%github_installations%'
				""").query(Long.class).single() > 0;
	}

	/**
	 * 테스트가 놓아 줄 때까지 기다린다. 제한 시간 안에 놓아 주지 않거나 인터럽트되면 예외를 던져, 기다리던 트랜잭션이 커밋되지 않고
	 * 롤백되게 한다 (CodeRabbit PR #29 리뷰). 제한은 테스트가 놓아 주기 전에 기다리는 시간(잠금 대기 관찰 등, 길어야 10초 남짓)보다 넉넉하게 둔다.
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

}
