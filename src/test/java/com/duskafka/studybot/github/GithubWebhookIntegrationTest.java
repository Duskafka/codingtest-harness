package com.duskafka.studybot.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.github.webhook.GithubEvent;
import com.duskafka.studybot.github.webhook.GithubWebhookEvent;
import com.duskafka.studybot.github.webhook.HandlingResult;
import com.duskafka.studybot.github.webhook.WebhookHandlingException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * S06 수용 기준: GitHub 웹훅 수신 (5.3 P1, 응답 코드 규칙). push 처리기 자리는 테스트 처리기가 채운다
 * ({@link WebhookTestConfiguration.RecordingPushHandler}).
 */
@ExtendWith(OutputCaptureExtension.class)
class GithubWebhookIntegrationTest extends WebhookTestSupport {

	private static final byte[] PUSH = payload("push.json");

	// --- T1: 서명 ---

	@Test
	@DisplayName("T1: 서명이 없으면 401이고 수신함에 행이 없다")
	void t1MissingSignatureIsRejected() {
		String delivery = newDeliveryId();

		MvcTestResult result = send("push", delivery, PUSH, null);

		assertThat(result).hasStatus(401);
		assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(inboxRows(delivery)).isEmpty();
		assertThat(pushHandler.calls()).isEmpty();
	}

	@Test
	@DisplayName("T1: 다른 시크릿으로 서명했으면 401이고 수신함에 행이 없다")
	void t1WrongSecretIsRejected() {
		String delivery = newDeliveryId();

		MvcTestResult result = send("push", delivery, PUSH, WebhookSigner.sign("not-the-secret", PUSH));

		assertThat(result).hasStatus(401);
		assertThat(inboxRows(delivery)).isEmpty();
		assertThat(pushHandler.calls()).isEmpty();
	}

	@Test
	@DisplayName("T1: 서명한 뒤 본문이 바뀌었으면 401이다 (원본 바이트로 확인)")
	void t1TamperedBodyIsRejected() {
		String delivery = newDeliveryId();
		byte[] tampered = new String(PUSH, StandardCharsets.UTF_8).replace("refs/heads/main", "refs/heads/evil")
			.getBytes(StandardCharsets.UTF_8);

		MvcTestResult result = send("push", delivery, tampered, WebhookSigner.sign(PUSH));

		assertThat(result).hasStatus(401);
		assertThat(inboxRows(delivery)).isEmpty();
	}

	@Test
	@DisplayName("S06: 16진수가 아닌 서명은 401이다")
	void nonHexSignatureIsRejected() {
		String delivery = newDeliveryId();

		MvcTestResult result = send("push", delivery, PUSH, "sha256=" + "zz".repeat(32));

		assertThat(result).hasStatus(401);
		assertThat(inboxRows(delivery)).isEmpty();
	}

	@Test
	@DisplayName("T1: 관심 없는 이벤트라도 서명이 없으면 401이다 (서명을 먼저 본다)")
	void t1UnsignedUninterestingEventIsRejected() {
		MvcTestResult result = send("issues", newDeliveryId(), payload("issues.json"), null);

		assertThat(result).hasStatus(401);
	}

	@Test
	@DisplayName("S06: 시크릿을 교체하는 동안에는 옛 시크릿과 새 시크릿으로 서명한 요청이 모두 통과한다")
	void bothSecretsAreAcceptedDuringRotation() {
		String withCurrent = newDeliveryId();
		String withPrevious = newDeliveryId();

		MvcTestResult current = send("push", withCurrent, PUSH, WebhookSigner.sign(WebhookSigner.SECRET, PUSH));
		MvcTestResult previous = send("push", withPrevious, PUSH,
				WebhookSigner.sign(WebhookSigner.PREVIOUS_SECRET, PUSH));

		assertThat(current).hasStatus(202);
		assertThat(previous).hasStatus(202);
		assertThat(inboxRows(withCurrent)).hasSize(1);
		assertThat(inboxRows(withPrevious)).hasSize(1);
	}

	// --- T2: 중복 ---

	@Test
	@DisplayName("T2: 같은 X-GitHub-Delivery를 두 번 받으면 수신함은 1행이고 처리기는 한 번만 불린다 (두 번째는 200)")
	void t2SameDeliveryTwiceIsStoredAndHandledOnce() {
		String delivery = newDeliveryId();

		MvcTestResult first = sendSigned("push", delivery, PUSH);
		MvcTestResult second = sendSigned("push", delivery, PUSH);

		assertThat(first).hasStatus(202);
		assertThat(second).hasStatus(200);
		assertThat(inboxRows(delivery)).hasSize(1);
		assertThat(pushHandler.calls()).hasSize(1);
	}

	@Test
	@DisplayName("T2: 같은 전달이 동시에 두 번 와도 수신함은 1행이고 처리기는 한 번만 불린다")
	void t2SameDeliveryConcurrentlyIsStoredAndHandledOnce() throws Exception {
		String delivery = newDeliveryId();

		List<Integer> statuses = sendConcurrently(delivery, 2);

		assertThat(statuses).containsExactlyInAnyOrder(202, 200);
		assertThat(inboxRows(delivery)).hasSize(1);
		assertThat(pushHandler.calls()).hasSize(1);
	}

	@Test
	@DisplayName("S06: 본문이 같아도 전달 ID가 다르면 따로 남기고 따로 처리한다 (중복은 전달 ID로만 판단)")
	void differentDeliveriesAreStoredSeparately() {
		String first = newDeliveryId();
		String second = newDeliveryId();

		assertThat(sendSigned("push", first, PUSH)).hasStatus(202);
		assertThat(sendSigned("push", second, PUSH)).hasStatus(202);

		assertThat(inboxRows(first)).hasSize(1);
		assertThat(inboxRows(second)).hasSize(1);
		assertThat(pushHandler.calls()).extracting(GithubWebhookEvent::deliveryId).containsExactly(first, second);
	}

	// --- 응답 코드 ---

	@Test
	@DisplayName("S06: ping은 200이고, 수신함에 PROCESSED로 남는다. 같은 ping을 다시 받아도 200이다")
	void pingIsStoredAndAnsweredWith200() {
		String delivery = newDeliveryId();

		MvcTestResult first = sendSigned("ping", delivery, payload("ping.json"));
		MvcTestResult again = sendSigned("ping", delivery, payload("ping.json"));

		assertThat(first).hasStatus(200);
		assertThat(again).hasStatus(200);
		InboxRow row = onlyInboxRow(delivery);
		assertThat(row.eventType()).isEqualTo("ping");
		assertThat(row.status()).isEqualTo("PROCESSED");
	}

	@Test
	@DisplayName("S06: 관심 없는 이벤트는 204이고 수신함에 남기지 않는다")
	void uninterestingEventIsAnsweredWith204() {
		String delivery = newDeliveryId();

		MvcTestResult result = sendSigned("issues", delivery, payload("issues.json"));

		assertThat(result).hasStatus(204);
		assertThat(inboxRows(delivery)).isEmpty();
		assertThat(pushHandler.calls()).isEmpty();
	}

	@Test
	@DisplayName("S06: 저장에 성공하면 202이고, 수신함에 원본 JSON과 처리 결과가 남는다")
	void storedDeliveryIsAnsweredWith202() {
		String delivery = newDeliveryId();

		MvcTestResult result = sendSigned("push", delivery, PUSH);

		assertThat(result).hasStatus(202);
		InboxRow row = onlyInboxRow(delivery);
		assertThat(row.source()).isEqualTo("GITHUB");
		assertThat(row.eventType()).isEqualTo("push");
		assertThat(row.payload()).isNotNull();
		assertThat(json(row.payload())).isEqualTo(json(PUSH));
		assertThat(row.status()).isEqualTo("PROCESSED");
		assertThat(row.attempts()).isEqualTo(1);
		assertThat(row.lastError()).isNull();
		assertThat(row.nextAttemptAt()).isNull();

		assertThat(pushHandler.calls()).singleElement().satisfies(event -> {
			assertThat(event.inboxId()).isEqualTo(row.id());
			assertThat(event.deliveryId()).isEqualTo(delivery);
			assertThat(event.event()).isEqualTo(GithubEvent.PUSH);
			assertThat(event.payload().get("ref").asString()).isEqualTo("refs/heads/main");
			assertThat(event.receivedAt()).isEqualTo(row.receivedAt());
		});
	}

	@Test
	@DisplayName("S06: 수신 시각(received_at)과 처리 시각(processed_at)은 주입된 Clock에서 온다")
	void timesComeFromInjectedClock() {
		Instant receivedAt = Instant.parse("2026-11-20T12:13:05Z");
		clock.setInstant(receivedAt);
		pushHandler.willDo(event -> {
			clock.advance(Duration.ofSeconds(3));
			return HandlingResult.PROCESSED;
		});
		String delivery = newDeliveryId();

		sendSigned("push", delivery, PUSH);

		InboxRow row = onlyInboxRow(delivery);
		assertThat(row.receivedAt()).isEqualTo(receivedAt);
		assertThat(row.processedAt()).isEqualTo(receivedAt.plusSeconds(3));
		assertThat(pushHandler.calls().getFirst().receivedAt()).isEqualTo(receivedAt);
	}

	@Test
	@DisplayName("S06: 처리기가 IGNORED를 돌려주면 수신함 상태는 IGNORED다")
	void handlerResultIgnoredIsRecorded() {
		pushHandler.willDo(event -> HandlingResult.IGNORED);
		String delivery = newDeliveryId();

		assertThat(sendSigned("push", delivery, PUSH)).hasStatus(202);

		InboxRow row = onlyInboxRow(delivery);
		assertThat(row.status()).isEqualTo("IGNORED");
		assertThat(row.attempts()).isEqualTo(1);
		assertThat(row.processedAt()).isNotNull();
	}

	@Test
	@DisplayName("S06: 받는 이벤트인데 처리기가 아직 없으면(installation 계열, S07 전) 202이고 IGNORED로 남는다")
	void acceptedEventWithoutHandlerIsIgnored() {
		String installation = newDeliveryId();
		String repositories = newDeliveryId();

		assertThat(sendSigned("installation", installation, payload("installation.json"))).hasStatus(202);
		assertThat(sendSigned("installation_repositories", repositories, payload("installation_repositories.json")))
			.hasStatus(202);

		assertThat(onlyInboxRow(installation).status()).isEqualTo("IGNORED");
		assertThat(onlyInboxRow(repositories).status()).isEqualTo("IGNORED");
		assertThat(onlyInboxRow(repositories).eventType()).isEqualTo("installation_repositories");
	}

	// --- 처리 실패와 트랜잭션 경계 ---

	@Test
	@DisplayName("S06: 처리기가 예외를 던져도 202이고, 수신함 상태는 FAILED이며 오류 내용(예외 종류)이 남는다. 메시지는 남기지 않는다")
	void handlerFailureIsRecordedAsFailed() {
		pushHandler.willDo(event -> {
			throw new IllegalStateException("tester@example.com 커밋 처리 실패");
		});
		String delivery = newDeliveryId();

		MvcTestResult result = sendSigned("push", delivery, PUSH);

		assertThat(result).hasStatus(202);
		InboxRow row = onlyInboxRow(delivery);
		assertThat(row.status()).isEqualTo("FAILED");
		assertThat(row.lastError()).isEqualTo("java.lang.IllegalStateException");
		assertThat(row.attempts()).isEqualTo(1);
		assertThat(row.processedAt()).isNull();
	}

	@Test
	@DisplayName("S06: 처리기가 WebhookHandlingException으로 준 문장은 last_error에 남는다")
	void handlerSafeMessageIsRecorded() {
		pushHandler.willDo(event -> {
			throw new WebhookHandlingException("연결하지 않은 레포예요");
		});
		String delivery = newDeliveryId();

		assertThat(sendSigned("push", delivery, PUSH)).hasStatus(202);

		assertThat(onlyInboxRow(delivery).lastError())
			.isEqualTo("com.duskafka.studybot.github.webhook.WebhookHandlingException: 연결하지 않은 레포예요");
	}

	@Test
	@DisplayName("S06: 처리 중 DB 오류면 last_error에 SQLState는 남지만 오류 메시지의 키 값은 남지 않는다")
	void databaseErrorKeepsSqlStateButNotValues() {
		long githubId = ThreadLocalRandom.current().nextLong(9_000_000_000L, 9_999_999_999L);
		pushHandler.willDo(event -> {
			for (int i = 0; i < 2; i++) {
				jdbc.sql("insert into studybot.users (github_id, github_login, created_at) values (:githubId, :login, :createdAt)")
					.param("githubId", githubId)
					.param("login", "webhook-duplicate-" + i)
					.param("createdAt", Timestamp.from(clock.instant()))
					.update();
			}
			return HandlingResult.PROCESSED;
		});
		String delivery = newDeliveryId();

		assertThat(sendSigned("push", delivery, PUSH)).hasStatus(202);

		InboxRow row = onlyInboxRow(delivery);
		assertThat(row.status()).isEqualTo("FAILED");
		assertThat(row.lastError()).contains("SQLState 23505").doesNotContain(String.valueOf(githubId));
	}

	@Test
	@DisplayName("S06: 처리기가 실패하면 처리기가 바꾼 DB 내용은 롤백되고 수신함 행은 남는다")
	void handlerChangesAreRolledBackOnFailure() {
		String login = "webhook-rollback-" + newDeliveryId();
		AtomicReference<Long> countInsideHandler = new AtomicReference<>();
		pushHandler.willDo(event -> {
			jdbc.sql("insert into studybot.users (github_id, github_login, created_at) values (:githubId, :login, :createdAt)")
				.param("githubId", ThreadLocalRandom.current().nextLong(9_000_000_000L, 9_999_999_999L))
				.param("login", login)
				.param("createdAt", Timestamp.from(clock.instant()))
				.update();
			countInsideHandler.set(usersWithLogin(login));
			throw new IllegalStateException("기록 뒤 실패");
		});
		String delivery = newDeliveryId();

		assertThat(sendSigned("push", delivery, PUSH)).hasStatus(202);

		assertThat(countInsideHandler.get()).isEqualTo(1L);
		assertThat(usersWithLogin(login)).isZero();
		assertThat(onlyInboxRow(delivery).status()).isEqualTo("FAILED");
	}

	@Test
	@DisplayName("S06: 처리기가 불릴 때 수신함 행은 이미 커밋돼 있고, 처리기는 따로 트랜잭션 안에서 돈다")
	void inboxIsCommittedBeforeHandlerRunsInItsOwnTransaction() {
		AtomicReference<String> statusSeenByOtherConnection = new AtomicReference<>();
		AtomicBoolean transactionActive = new AtomicBoolean();
		pushHandler.willDo(event -> {
			transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
			// 다른 스레드는 이 트랜잭션의 연결을 쓰지 않는다. 커밋된 행만 보인다.
			statusSeenByOtherConnection.set(CompletableFuture
				.supplyAsync(() -> jdbc.sql("select status from studybot.webhook_inbox where id = :id")
					.param("id", event.inboxId())
					.query(String.class)
					.optional()
					.orElse("없음"))
				.join());
			return HandlingResult.PROCESSED;
		});

		assertThat(sendSigned("push", newDeliveryId(), PUSH)).hasStatus(202);

		assertThat(statusSeenByOtherConnection.get()).isEqualTo("RECEIVED");
		assertThat(transactionActive).isTrue();
	}

	@Test
	@DisplayName("S06: 처리기는 성공했지만 커밋이 실패하면 처리기 변경은 롤백되고 FAILED로 남는다")
	void commitFailureAfterHandlerIsRecordedAsFailed() {
		pushHandler.willDo(event -> {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

				@Override
				public void beforeCommit(boolean readOnly) {
					throw new IllegalStateException("커밋 직전 실패");
				}

			});
			return HandlingResult.PROCESSED;
		});
		String delivery = newDeliveryId();

		assertThat(sendSigned("push", delivery, PUSH)).hasStatus(202);

		InboxRow row = onlyInboxRow(delivery);
		assertThat(row.status()).isEqualTo("FAILED");
		assertThat(row.lastError()).contains("java.lang.IllegalStateException").doesNotContain("커밋 직전 실패");
		assertThat(row.processedAt()).isNull();
	}

	@Test
	@DisplayName("S06: 오류 내용이 길면 잘라서 남긴다")
	void longErrorIsTruncated() {
		String longMessage = "x".repeat(5000);
		pushHandler.willDo(event -> {
			throw new WebhookHandlingException(longMessage);
		});
		String delivery = newDeliveryId();

		sendSigned("push", delivery, PUSH);

		assertThat(onlyInboxRow(delivery).lastError()).hasSize(2000)
			.startsWith("com.duskafka.studybot.github.webhook.WebhookHandlingException: xxx");
	}

	// --- 본문과 헤더 ---

	@Test
	@DisplayName("S06: 본문이 5MB보다 크면 413이고 수신함에 남기지 않는다")
	void bodyLargerThanLimitIsRejected() {
		byte[] body = jsonOfSize(5 * 1024 * 1024 + 1);
		String delivery = newDeliveryId();

		MvcTestResult result = sendSigned("push", delivery, body);

		assertThat(result).hasStatus(413);
		assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(inboxRows(delivery)).isEmpty();
		assertThat(pushHandler.calls()).isEmpty();
	}

	@Test
	@DisplayName("S06: 본문이 한도를 넘어 거절하면 전달 ID를 경고 로그로 남긴다 (한도를 올린 뒤 GitHub에서 재전송할 수 있게)")
	void oversizedBodyIsLoggedWithDeliveryId(CapturedOutput output) {
		String delivery = newDeliveryId();
		// 서명 확인 전이라 헤더는 믿을 수 없다. 긴 값은 잘라서 남긴다. 줄바꿈 같은 제어 문자는 Spring Security 방화벽이 먼저 막는다.
		String longHeader = "d".repeat(100) + "-로그에-남으면-안-되는-뒷부분";

		assertThat(sendSigned("push", delivery, jsonOfSize(5 * 1024 * 1024 + 1))).hasStatus(413);
		assertThat(send("push", longHeader, jsonOfSize(5 * 1024 * 1024 + 1), null)).hasStatus(413);

		assertThat(output.getOut()).contains("WARN").contains("delivery=" + delivery).contains("event=push");
		assertThat(output.getOut()).contains("delivery=" + "d".repeat(100) + "…").doesNotContain("로그에-남으면-안-되는-뒷부분");
	}

	@Test
	@DisplayName("S06: 본문이 정확히 5MB면 받는다")
	void bodyAtLimitIsAccepted() {
		byte[] body = jsonOfSize(5 * 1024 * 1024);
		String delivery = newDeliveryId();

		assertThat(sendSigned("push", delivery, body)).hasStatus(202);
		assertThat(inboxRows(delivery)).hasSize(1);
	}

	@Test
	@DisplayName("S06: 서명은 맞지만 X-GitHub-Delivery가 없으면 400이다 (중복을 가릴 수 없음)")
	void missingDeliveryIdIsBadRequest() {
		MvcTestResult result = send("push", null, PUSH, WebhookSigner.sign(PUSH));

		assertThat(result).hasStatus(400);
		assertThat(pushHandler.calls()).isEmpty();
	}

	@Test
	@DisplayName("S06: 서명은 맞지만 X-GitHub-Event가 없으면 400이다")
	void missingEventIsBadRequest() {
		String delivery = newDeliveryId();

		MvcTestResult result = send(null, delivery, PUSH, WebhookSigner.sign(PUSH));

		assertThat(result).hasStatus(400);
		assertThat(inboxRows(delivery)).isEmpty();
	}

	@Test
	@DisplayName("S06: 서명은 맞지만 본문이 JSON 객체가 아니면 400이고 수신함에 남기지 않는다")
	void nonJsonBodyIsBadRequest() {
		String notJson = newDeliveryId();
		String notObject = newDeliveryId();

		assertThat(sendSigned("push", notJson, "payload=%7B%7D".getBytes(StandardCharsets.UTF_8))).hasStatus(400);
		assertThat(sendSigned("push", notObject, "[1, 2]".getBytes(StandardCharsets.UTF_8))).hasStatus(400);

		assertThat(inboxRows(notJson)).isEmpty();
		assertThat(inboxRows(notObject)).isEmpty();
	}

	@Test
	@DisplayName("S06: webhook_inbox는 studybot 스키마에 있다 (결정 제안서 004)")
	void inboxTableIsInStudybotSchema() {
		assertThat(jdbc.sql("select table_schema from information_schema.tables where table_name = 'webhook_inbox'")
			.query(String.class)
			.list()).containsExactly("studybot");
	}

	private long usersWithLogin(String login) {
		return jdbc.sql("select count(*) from studybot.users where github_login = :login")
			.param("login", login)
			.query(Long.class)
			.single();
	}

	/** {@code {"pad":"xxx…"}} 꼴의 JSON을 정확히 {@code size}바이트로 만든다. */
	private static byte[] jsonOfSize(int size) {
		byte[] prefix = "{\"pad\":\"".getBytes(StandardCharsets.UTF_8);
		byte[] suffix = "\"}".getBytes(StandardCharsets.UTF_8);
		byte[] body = new byte[size];
		System.arraycopy(prefix, 0, body, 0, prefix.length);
		Arrays.fill(body, prefix.length, size - suffix.length, (byte) 'x');
		System.arraycopy(suffix, 0, body, size - suffix.length, suffix.length);
		return body;
	}

	private List<Integer> sendConcurrently(String delivery, int count) throws Exception {
		CountDownLatch ready = new CountDownLatch(count);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(count);
		try {
			List<Future<MvcTestResult>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					go.await();
					return sendSigned("push", delivery, PUSH);
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<MvcTestResult> future : futures) {
				statuses.add(future.get(30, TimeUnit.SECONDS).getResponse().getStatus());
			}
			return statuses;
		}
		finally {
			executor.shutdownNow();
		}
	}

}
