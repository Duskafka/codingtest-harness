package com.duskafka.studybot.github.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.github.GithubApiStub;
import com.duskafka.studybot.github.GithubAppTestKeys;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * S07 만들 것 4: 설치 토큰(1시간) 발급과 만료 전 갱신 캐시. GitHub API는 WireMock({@link GithubApiStub})이다.
 *
 * <p>수용 기준: 설치 토큰은 만료 전에 갱신되고, 유효한 동안은 다시 발급하지 않는다. private key와 토큰이 로그에 남지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class InstallationTokenCacheTest {

	private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

	private final MutableClock clock = new MutableClock();

	private InstallationTokenCache cache;

	@BeforeEach
	void setUp() {
		GithubApiStub.reset();
		clock.setInstant(NOW);
		GithubAppJwt jwt = new GithubAppJwt("test-client-id", PemPrivateKeys.parse(GithubAppTestKeys.pkcs1Pem()), clock);
		cache = new InstallationTokenCache(GithubApiClient.create(URI.create(GithubApiStub.baseUrl()), jwt), clock);
	}

	@Test
	@DisplayName("S07: 설치 토큰은 유효한 동안 다시 발급하지 않는다")
	void reusesValidToken() {
		GithubApiStub.stubInstallationToken(101, NOW.plus(Duration.ofHours(1)));

		String first = cache.tokenFor(101);
		clock.advance(Duration.ofMinutes(54));
		String second = cache.tokenFor(101);

		assertThat(first).isEqualTo(GithubApiStub.tokenFor(101, 1));
		assertThat(second).isEqualTo(first);
		assertThat(GithubApiStub.tokenRequests(101)).hasSize(1);
	}

	@Test
	@DisplayName("S07: 설치 토큰은 만료 5분 전부터 새로 발급한다")
	void refreshesBeforeExpiry() {
		Instant firstExpiry = NOW.plus(Duration.ofHours(1));
		GithubApiStub.stubInstallationTokens(102, List.of(firstExpiry, firstExpiry.plus(Duration.ofHours(1))));
		cache.tokenFor(102);

		clock.setInstant(firstExpiry.minus(Duration.ofMinutes(5)));
		String refreshed = cache.tokenFor(102);

		assertThat(refreshed).isEqualTo(GithubApiStub.tokenFor(102, 2));
		assertThat(GithubApiStub.tokenRequests(102)).hasSize(2);
		assertThat(cache.tokenFor(102)).as("새 토큰은 다시 캐시된다").isEqualTo(refreshed);
		assertThat(GithubApiStub.tokenRequests(102)).hasSize(2);
	}

	@Test
	@DisplayName("S07: 설치마다 따로 발급하고, 동시에 불러도 설치 하나에 한 번만 발급한다")
	void issuesOncePerInstallationEvenConcurrently() throws Exception {
		GithubApiStub.stubInstallationToken(103, NOW.plus(Duration.ofHours(1)));
		GithubApiStub.stubInstallationToken(104, NOW.plus(Duration.ofHours(1)));
		int threads = 8;
		CountDownLatch start = new CountDownLatch(1);
		List<Callable<String>> calls = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			calls.add(() -> {
				start.await();
				return cache.tokenFor(103);
			});
		}
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			List<Future<String>> futures = new ArrayList<>();
			for (Callable<String> call : calls) {
				futures.add(executor.submit(call));
			}
			start.countDown();
			for (Future<String> future : futures) {
				assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(GithubApiStub.tokenFor(103, 1));
			}
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(cache.tokenFor(104)).isEqualTo(GithubApiStub.tokenFor(104, 1));
		assertThat(GithubApiStub.tokenRequests(103)).hasSize(1);
		assertThat(GithubApiStub.tokenRequests(104)).hasSize(1);
	}

	@Test
	@DisplayName("S07: 토큰 발급은 App JWT(Bearer)로 하고 GitHub가 권하는 헤더를 붙인다")
	void tokenRequestIsAuthenticatedWithAppJwt() throws Exception {
		GithubApiStub.stubInstallationToken(105, NOW.plus(Duration.ofHours(1)));

		cache.tokenFor(105);

		LoggedRequest request = GithubApiStub.tokenRequests(105).getFirst();
		assertThat(request.getHeader("Accept")).isEqualTo("application/vnd.github+json");
		assertThat(request.getHeader("X-GitHub-Api-Version")).isEqualTo(GithubApiStub.API_VERSION);
		String authorization = request.getHeader("Authorization");
		assertThat(authorization).startsWith("Bearer ");
		String[] parts = authorization.substring("Bearer ".length()).split("\\.");
		Signature verifier = Signature.getInstance("SHA256withRSA");
		verifier.initVerify(GithubAppTestKeys.publicKey());
		verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
		assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
	}

	@Test
	@DisplayName("S07: 토큰 발급이 실패하면 503 예외이고, 다음 호출에서 다시 발급을 시도한다")
	void failureIsUnavailableAndRetriedNextTime() {
		GithubApiStub.stubInstallationTokenFailure(106, 500);

		assertThatThrownBy(() -> cache.tokenFor(106)).isInstanceOf(GithubApiUnavailableException.class);

		GithubApiStub.reset();
		GithubApiStub.stubInstallationToken(106, NOW.plus(Duration.ofHours(1)));
		assertThat(cache.tokenFor(106)).isEqualTo(GithubApiStub.tokenFor(106, 1));
	}

	@Test
	@DisplayName("S07: 성공하든 실패하든 private key, App JWT, 설치 토큰이 로그와 예외 메시지에 남지 않는다")
	void secretsAreNotLogged(CapturedOutput output) {
		GithubApiStub.stubInstallationToken(107, NOW.plus(Duration.ofHours(1)));
		GithubApiStub.stubInstallationTokenFailure(108, 401);
		GithubApiStub.stubInstallationTokenFailure(109, 503);

		String token = cache.tokenFor(107);
		InstallationToken issued = new InstallationToken(token, NOW);
		Throwable unauthorized = catchFailure(108);
		Throwable unavailable = catchFailure(109);

		String jwt = GithubApiStub.tokenRequests(107).getFirst().getHeader("Authorization").substring("Bearer ".length());
		String everything = output.getAll() + unauthorized + unauthorized.getCause() + unavailable + unavailable.getCause()
				+ issued;
		assertThat(everything).doesNotContain(token)
			.doesNotContain(jwt)
			.doesNotContain(GithubAppTestKeys.pkcs1Base64().substring(0, 40))
			.doesNotContain("PRIVATE KEY");
	}

	private Throwable catchFailure(long installationId) {
		try {
			cache.tokenFor(installationId);
		}
		catch (GithubApiUnavailableException ex) {
			return ex;
		}
		throw new AssertionError("예외가 나야 한다");
	}

}
