package com.duskafka.studybot.github.installation;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.github.GithubConnectionRows;
import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import com.duskafka.studybot.github.webhook.HandlingResult;
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
 * <p>deleted가 행을 쥔 채 멈춰 있는 동안 suspend를 보낸다. 잠금이 없으면 suspend가 ACTIVE를 읽고 SUSPENDED로 덮어써서
 * PROCESSED가 된다. 기다리는 시간은 '잠금이 없을 때 suspend가 먼저 읽을 여유'일 뿐이라, 잠그는 코드에서는 결과가 흔들리지 않는다.
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
					HandlingResult result = this.linker.deleted(installationId, null);
					deleteApplied.countDown();
					awaitQuietly(commitDelete);
					return result;
				}));
			assertThat(deleteApplied.await(10, TimeUnit.SECONDS)).as("deleted가 먼저 반영됨").isTrue();

			Future<HandlingResult> suspending = executor.submit(() -> this.linker.suspended(installationId, null));
			Thread.sleep(300);
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

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
