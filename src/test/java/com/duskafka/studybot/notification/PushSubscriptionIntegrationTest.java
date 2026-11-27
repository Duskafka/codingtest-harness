package com.duskafka.studybot.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.notification.NotificationRows.SubscriptionRow;
import com.duskafka.studybot.progress.StudyDayRows;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 푸시 구독 API (S13 만들 것 5, api.md 4.4, T28, T19). {@code GET /api/push/vapid-public-key}(공개),
 * {@code POST /api/me/push-subscriptions}(로그인), {@code DELETE /api/me/push-subscriptions/{id}}(본인).
 */
@IntegrationTest
class PushSubscriptionIntegrationTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	private StudyDayRows rows;

	private NotificationRows notifications;

	private TestApi api;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		notifications = new NotificationRows(jdbc);
		api = new TestApi(mvc, jdbc);
	}

	private static String fcmEndpoint() {
		return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
	}

	/** 브라우저 {@code PushSubscription.toJSON()} 모양. */
	private static String subscription(String endpoint, PushReceiver receiver) {
		ObjectNode node = JSON.createObjectNode();
		node.put("endpoint", endpoint);
		node.putNull("expirationTime");
		ObjectNode keys = node.putObject("keys");
		keys.put("p256dh", receiver.p256dh());
		keys.put("auth", receiver.auth());
		return JSON.writeValueAsString(node);
	}

	private MvcTestResult register(long userId, String endpoint, PushReceiver receiver) {
		return api.postJson(userId, "/api/me/push-subscriptions", subscription(endpoint, receiver));
	}

	@Test
	@DisplayName("VAPID 공개키는 로그인 없이 읽는다. 브라우저 applicationServerKey 형식(압축하지 않은 점의 base64url)이다")
	void vapidPublicKeyIsPublic() {
		MvcTestResult result = api.getAnonymous("/api/push/vapid-public-key");

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(TestApi.json(result).path("publicKey").asString())
			.isEqualTo(WebPushTestKeys.vapidPublicKeyBase64Url());
	}

	@Test
	@DisplayName("구독을 등록하면 201과 구독 ID를 주고 브라우저가 준 키를 그대로 저장한다")
	void registersSubscription() {
		long alice = rows.newUser("s13-sub");
		String endpoint = fcmEndpoint();
		PushReceiver receiver = PushReceiver.create();

		MvcTestResult result = register(alice, endpoint, receiver);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		JsonNode body = TestApi.json(result);
		List<SubscriptionRow> saved = notifications.subscriptionsByEndpoint(endpoint);
		assertThat(saved).singleElement().satisfies(row -> {
			assertThat(row.id()).isEqualTo(body.path("id").asLong());
			assertThat(row.userId()).isEqualTo(alice);
			assertThat(row.p256dh()).isEqualTo(receiver.p256dh());
			assertThat(row.auth()).isEqualTo(receiver.auth());
		});
		assertThat(body.path("createdAt").isMissingNode()).isFalse();
	}

	@Test
	@DisplayName("같은 endpoint를 다시 등록하면 새 행을 만들지 않고 키와 사용자를 지금 값으로 바꾼다 (기기 하나에 구독 하나)")
	void reRegisteringSameEndpointUpdatesInPlace() {
		long alice = rows.newUser("s13-sub-again");
		long bob = rows.newUser("s13-sub-other");
		String endpoint = fcmEndpoint();
		long first = TestApi.json(register(alice, endpoint, PushReceiver.create())).path("id").asLong();
		PushReceiver renewed = PushReceiver.create();

		MvcTestResult result = register(bob, endpoint, renewed);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(TestApi.json(result).path("id").asLong()).isEqualTo(first);
		assertThat(notifications.subscriptionsByEndpoint(endpoint)).singleElement().satisfies(row -> {
			assertThat(row.userId()).isEqualTo(bob);
			assertThat(row.p256dh()).isEqualTo(renewed.p256dh());
			assertThat(row.auth()).isEqualTo(renewed.auth());
		});
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "https://updates.push.services.mozilla.com/wpush/v2/gAAAAABexample",
			"https://web.push.apple.com/QGuQyavXutnMH8example", "https://wns2-par02p.notify.windows.com/w/?token=BQYAAABexample" })
	@DisplayName("T28: 알려진 푸시 서비스(Mozilla, Apple, Windows)의 주소는 받는다")
	void t28KnownPushServicesAreAccepted(String endpoint) {
		long alice = rows.newUser("s13-known");

		assertThat(register(alice, endpoint + UUID.randomUUID(), PushReceiver.create())).hasStatus(HttpStatus.CREATED);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "https://evil.example.com/push/1", "http://fcm.googleapis.com/fcm/send/abc",
			"https://169.254.169.254/latest/meta-data", "https://user@fcm.googleapis.com/fcm/send/abc",
			"https://fcm.googleapis.com.evil.example/fcm/send/abc", "https://evilnotify.windows.com/w/abc",
			"https://fcm.googleapis.com:8443/fcm/send/abc", "https://localhost/push/abc", "ftp://fcm.googleapis.com/abc",
			"not a url" })
	@DisplayName("T28: 알려진 푸시 서비스가 아닌 구독 주소는 400으로 거절하고 저장하지 않는다 (SSRF 방지, 9.7)")
	void t28UnknownPushServicesAreRejected(String endpoint) {
		long alice = rows.newUser("s13-unknown");

		assertThat(register(alice, endpoint, PushReceiver.create())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(notifications.subscriptionsByEndpoint(endpoint)).isEmpty();
	}

	@Test
	@DisplayName("키 형식이 틀리면 400이다: p256dh는 압축하지 않은 P-256 점(65바이트), auth는 16바이트")
	void malformedKeysAreRejected() {
		long alice = rows.newUser("s13-bad-keys");
		PushReceiver receiver = PushReceiver.create();
		ObjectNode shortKey = (ObjectNode) JSON.readTree(subscription(fcmEndpoint(), receiver));
		((ObjectNode) shortKey.get("keys")).put("p256dh", WebPushTestKeys.base64Url(new byte[64]));
		ObjectNode shortAuth = (ObjectNode) JSON.readTree(subscription(fcmEndpoint(), receiver));
		((ObjectNode) shortAuth.get("keys")).put("auth", WebPushTestKeys.base64Url(new byte[8]));
		ObjectNode noKeys = (ObjectNode) JSON.readTree(subscription(fcmEndpoint(), receiver));
		noKeys.remove("keys");
		byte[] notOnCurve = new byte[65];
		notOnCurve[0] = 4;
		ObjectNode badPoint = (ObjectNode) JSON.readTree(subscription(fcmEndpoint(), receiver));
		((ObjectNode) badPoint.get("keys")).put("p256dh", WebPushTestKeys.base64Url(notOnCurve));

		for (ObjectNode body : List.of(shortKey, shortAuth, noKeys, badPoint)) {
			assertThat(api.postJson(alice, "/api/me/push-subscriptions", JSON.writeValueAsString(body)))
				.hasStatus(HttpStatus.BAD_REQUEST);
		}
	}

	@Test
	@DisplayName("로그인하지 않으면 등록할 수 없다 (401)")
	void anonymousCannotRegister() {
		String endpoint = fcmEndpoint();

		assertThat(api.postJsonAnonymous("/api/me/push-subscriptions", subscription(endpoint, PushReceiver.create())))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(notifications.subscriptionsByEndpoint(endpoint)).isEmpty();
	}

	@Test
	@DisplayName("내 구독을 해제하면 204이고 행이 지워진다")
	void deletesOwnSubscription() {
		long alice = rows.newUser("s13-unsub");
		String endpoint = fcmEndpoint();
		long id = TestApi.json(register(alice, endpoint, PushReceiver.create())).path("id").asLong();

		assertThat(api.delete(alice, "/api/me/push-subscriptions/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(notifications.subscription(id)).isEmpty();
	}

	@Test
	@DisplayName("T19: 남의 구독은 지울 수 없다 (404, 구독은 그대로). 없는 구독도 404다")
	void t19CannotDeleteOthersSubscription() {
		long alice = rows.newUser("s13-owner-sub");
		long mallory = rows.newUser("s13-mallory");
		long id = TestApi.json(register(alice, fcmEndpoint(), PushReceiver.create())).path("id").asLong();

		assertThat(api.delete(mallory, "/api/me/push-subscriptions/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(notifications.subscription(id)).isPresent();
		assertThat(api.delete(mallory, "/api/me/push-subscriptions/{id}", Long.MAX_VALUE))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

}
