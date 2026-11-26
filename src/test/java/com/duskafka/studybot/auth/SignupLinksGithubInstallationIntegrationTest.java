package com.duskafka.studybot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.duskafka.studybot.github.GithubConnectionRows;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * S07 수용 기준: 설치를 먼저 하고 나중에 가입하면 가입할 때 연결된다 (5.2 처리 순서 2, S07 만들 것 2).
 *
 * <p>가입은 실제 로그인 흐름(GitHub 콜백)으로 한다. 설치는 웹훅이 남겼을 모양 그대로 DB에 넣는다 (웹훅 처리 자체는
 * {@code github.InstallationWebhookIntegrationTest}가 본다). 가입할 때도 웹훅과 같은 조건(개인 계정, 계정 주인이 설치)을 다시 본다.
 *
 * <p>연결은 로그인이 커밋된 뒤 다른 스레드에서 한다(CodeRabbit 리뷰 반영, 사람이 정함 2026-11-23 "비동기로 실행"). 그래서 결과를
 * 기다려서 본다. 연결되지 않아야 하는 경우는 같은 사람의 '연결돼야 하는 설치'가 연결될 때까지 기다린 뒤 확인한다
 * (아직 처리 전이라서 비어 있는 것과 구분하려고).
 */
class SignupLinksGithubInstallationIntegrationTest extends LoginTestSupport {

	private static final Duration WAIT = Duration.ofSeconds(10);

	@Autowired
	PlatformTransactionManager transactionManager;

	private GithubConnectionRows rows;

	@BeforeEach
	void setUpRows() {
		rows = new GithubConnectionRows(jdbc);
	}

	@Test
	@DisplayName("S07: 설치를 먼저 하고 나중에 가입하면 가입할 때 그 설치가 연결된다")
	void signupLinksEarlierInstallation() {
		long githubId = GithubConnectionRows.newGithubId();
		long installationId = eligibleInstallationOf(githubId);

		login(githubId, "s07-late-signup", "https://avatars.example.com/u/" + githubId);

		await().atMost(WAIT)
			.untilAsserted(() -> assertThat(rows.existingInstallation(installationId).userId())
				.isEqualTo(userIdOf(githubId)));
	}

	@Test
	@DisplayName("S07: 가입해도 조직 계정 설치나 남이 설치한 것(account.id ≠ sender.id)은 연결되지 않는다")
	void signupDoesNotLinkIneligibleInstallations() {
		long githubId = GithubConnectionRows.newGithubId();
		long organization = GithubConnectionRows.newInstallationId();
		long installedBySomeoneElse = GithubConnectionRows.newInstallationId();
		long deleted = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(organization, null, githubId, "Organization", githubId, "ACTIVE", clock.instant());
		rows.insertInstallation(installedBySomeoneElse, null, githubId, "User", GithubConnectionRows.newGithubId(),
				"ACTIVE", clock.instant());
		rows.insertInstallation(deleted, null, githubId, "User", githubId, "DELETED", clock.instant());
		long sentinel = eligibleInstallationOf(githubId);

		login(githubId, "s07-ineligible", "https://avatars.example.com/u/" + githubId);

		awaitLinked(sentinel, githubId);
		assertThat(rows.existingInstallation(organization).userId()).isNull();
		assertThat(rows.existingInstallation(installedBySomeoneElse).userId()).isNull();
		assertThat(rows.existingInstallation(deleted).userId()).isNull();
	}

	@Test
	@DisplayName("S07: 이미 다른 사용자에게 연결된 설치는 로그인으로 옮겨 가지 않는다")
	void loginDoesNotStealLinkedInstallation() {
		long githubId = GithubConnectionRows.newGithubId();
		var owner = rows.newUser("owner", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();
		// 실제로는 생길 수 없는 모양(계정 ID가 로그인하는 사람과 같은데 다른 사용자에게 연결됨)으로, 이미 정해진 user_id를 덮어쓰지 않는지 본다.
		rows.insertInstallation(installationId, owner.id(), githubId, "User", githubId, "ACTIVE", clock.instant());
		long sentinel = eligibleInstallationOf(githubId);

		login(githubId, "s07-no-steal", "https://avatars.example.com/u/" + githubId);

		awaitLinked(sentinel, githubId);
		assertThat(rows.existingInstallation(installationId).userId()).isEqualTo(owner.id());
	}

	@Test
	@DisplayName("S07: 로그인은 설치 연결을 기다리지 않는다. 연결이 막혀 있어도 로그인이 끝나고, 풀리면 연결된다 (CodeRabbit 리뷰)")
	void loginDoesNotWaitForInstallationClaim() throws Exception {
		long githubId = GithubConnectionRows.newGithubId();
		long installationId = eligibleInstallationOf(githubId);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			// 다른 트랜잭션이 설치 행을 쥐고 있어서 연결 UPDATE는 기다려야 한다.
			Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
				jdbc.sql("select installation_id from studybot.github_installations where installation_id = :id for update")
					.param("id", installationId)
					.query(Long.class)
					.single();
				locked.countDown();
				awaitRelease(release);
				return null;
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			Future<Login> login = executor
				.submit(() -> login(githubId, "s07-no-wait", "https://avatars.example.com/u/" + githubId));

			assertThat(login.get(5, TimeUnit.SECONDS).session()).as("행이 잠겨 있어도 로그인은 끝난다").isNotBlank();
			assertThat(rows.existingInstallation(installationId).userId()).as("연결은 아직 기다리는 중").isNull();
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);
			awaitLinked(installationId, githubId);
		}
		finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	/** 연결 조건을 넘는 설치(개인 계정, 계정 주인이 설치, 주인 없음)를 넣는다. */
	private long eligibleInstallationOf(long githubId) {
		long installationId = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(installationId, null, githubId, "User", githubId, "ACTIVE", clock.instant());
		return installationId;
	}

	private void awaitLinked(long installationId, long githubId) {
		await().atMost(WAIT)
			.untilAsserted(() -> assertThat(rows.existingInstallation(installationId).userId())
				.isEqualTo(userIdOf(githubId)));
	}

	/**
	 * 테스트가 놓아 줄 때까지 기다린다. 제한 시간 안에 놓아 주지 않거나 인터럽트되면 예외를 던져, 기다리던 트랜잭션이 커밋되지 않고
	 * 롤백되게 한다 (CodeRabbit PR #29 리뷰). 제한은 테스트가 잠금 대기를 지켜보는 제한(10초)보다 넉넉하게 둔다.
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
