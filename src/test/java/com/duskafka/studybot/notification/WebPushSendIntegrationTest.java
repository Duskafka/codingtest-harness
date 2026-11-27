package com.duskafka.studybot.notification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.notification.NotificationRows.NotificationRow;
import com.duskafka.studybot.notification.NotificationRows.SubscriptionRow;
import com.duskafka.studybot.notification.dispatch.NotificationDispatcher;
import com.duskafka.studybot.notification.reminder.ReminderGenerator;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 웹 푸시 발송 (S13 만들 것 6·7, 5.8, T17, OD-11 결정 제안서 009 '검증 방법'). 웹 푸시 서비스는 WireMock이다.
 *
 * <p>요청은 라이브러리를 믿지 않고 JDK로 검증한다: 본문을 구독 키로 RFC 8291대로 풀고, VAPID JWT 서명·클레임을 공개키로 확인한다
 * ({@link PushReceiver}).
 */
@IntegrationTest
class WebPushSendIntegrationTest {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private ReminderGenerator generator;

	@Autowired
	private NotificationDispatcher dispatcher;

	@Autowired
	private MeterRegistry meterRegistry;

	private StudyDayRows rows;

	private NotificationRows notifications;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		notifications = new NotificationRows(jdbc);
		notifications.expireLeftovers();
	}

	/** 공유 유도 알림 하나를 바로 넣는다 (발송 직전 재확인이 없는 종류). 지금 11/20 22:00, 만료 11/21 00:00. */
	private long sharePrompt(long studyId, long userId) {
		Instant now = seoul(NOV_20, 22, 0);
		clock.setInstant(now);
		return notifications.insertPending("SHARE_PROMPT", studyId, userId, NOV_20,
				"{\"type\":\"SHARE_PROMPT\",\"title\":\"S13\",\"body\":\"공유해 보세요\",\"url\":\"/studies/" + studyId
						+ "/share\"}",
				now, seoul(NOV_21, 0, 0));
	}

	@Test
	@DisplayName("201: SENT. 요청은 암호화한 본문(aes128gcm), VAPID 헤더, 마감까지의 TTL, Urgency, Topic을 갖는다")
	void createdIsSentWithEncryptedBodyAndVapidHeaders() {
		long alice = rows.newUser("s13-push");
		TestStudy study = rows.standardStudy(alice);
		String endpoint = WebPushStub.newEndpoint();
		WebPushStub.reply(endpoint, 201);
		PushReceiver receiver = PushReceiver.create();
		long subscription = notifications.insertSubscription(alice, endpoint, receiver);
		clock.setInstant(seoul(NOV_20, 22, 0));
		generator.remindStudy(study.studyId());
		NotificationRow reminder = notifications.ofStudy(study.studyId(), "REMINDER").getFirst();
		double sentBefore = counter("studybot.notification.dispatched", "result", "SENT");

		dispatcher.run();

		assertThat(notifications.byId(reminder.id()).status()).isEqualTo("SENT");
		LoggedRequest request = WebPushStub.requestsTo(endpoint).getFirst();
		assertThat(request.getHeader("Content-Encoding")).isEqualTo("aes128gcm");
		assertThat(request.getHeader("Content-Type")).isEqualTo("application/octet-stream");
		assertThat(request.getHeader("TTL")).isEqualTo("7200");
		assertThat(request.getHeader("Urgency")).isEqualTo("high");
		assertThat(request.getHeader("Topic")).matches("[A-Za-z0-9_-]{1,32}");
		assertThat(JSON.readTree(receiver.decrypt(request.getBody()))).isEqualTo(JSON.readTree(reminder.payload()));

		JsonNode claims = PushReceiver.verifyVapid(request.getHeader("Authorization"));
		assertThat(claims.path("aud").asString()).isEqualTo(WebPushStub.origin());
		assertThat(claims.path("sub").asString()).isEqualTo("mailto:studybot-test@example.invalid");
		long exp = claims.path("exp").asLong();
		assertThat(Instant.ofEpochSecond(exp)).isAfter(seoul(NOV_20, 22, 0))
			.isBeforeOrEqualTo(seoul(NOV_20, 22, 0).plus(Duration.ofHours(24)));

		SubscriptionRow after = notifications.subscription(subscription).orElseThrow();
		assertThat(after.lastSuccessAt()).isEqualTo(seoul(NOV_20, 22, 0));
		assertThat(after.failureCount()).isZero();
		assertThat(counter("studybot.notification.dispatched", "result", "SENT")).isEqualTo(sentBefore + 1);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(ints = { 410, 404 })
	@DisplayName("T17: 웹 푸시 서비스가 410(또는 404)을 주면 구독이 삭제되고 알림은 FAILED다")
	void t17GoneSubscriptionIsDeleted(int status) {
		long alice = rows.newUser("s13-gone");
		TestStudy study = rows.standardStudy(alice);
		String endpoint = WebPushStub.newEndpoint();
		WebPushStub.reply(endpoint, status);
		long subscription = notifications.insertSubscription(alice, endpoint, PushReceiver.create());
		long id = sharePrompt(study.studyId(), alice);
		double removedBefore = counter("studybot.notification.subscriptions.removed");

		dispatcher.run();

		assertThat(notifications.subscription(subscription)).isEmpty();
		NotificationRow row = notifications.byId(id);
		assertThat(row.status()).isEqualTo("FAILED");
		assertThat(row.lastError()).isEqualTo("SUBSCRIPTION_GONE");
		assertThat(row.attempts()).isEqualTo(1);
		assertThat(counter("studybot.notification.subscriptions.removed")).isEqualTo(removedBefore + 1);
	}

	@Test
	@DisplayName("429: Retry-After만큼 재시도 시각이 뒤로 밀리고 시도 횟수가 오른다. 다시 발송 대기다")
	void tooManyRequestsHonorsRetryAfter() {
		long alice = rows.newUser("s13-429");
		TestStudy study = rows.standardStudy(alice);
		String endpoint = WebPushStub.newEndpoint();
		WebPushStub.replyWithRetryAfter(endpoint, 429, "120");
		long subscription = notifications.insertSubscription(alice, endpoint, PushReceiver.create());
		long id = sharePrompt(study.studyId(), alice);

		dispatcher.run();

		NotificationRow row = notifications.byId(id);
		assertThat(row.status()).isEqualTo("PENDING");
		assertThat(row.attempts()).isEqualTo(1);
		assertThat(row.nextAttemptAt()).isEqualTo(seoul(NOV_20, 22, 2));
		assertThat(row.lockedUntil()).isNull();
		assertThat(row.lastError()).isEqualTo("HTTP_429");
		assertThat(notifications.subscription(subscription).orElseThrow().failureCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("5xx: 재시도 시각이 뒤로 밀리고 시도 횟수가 오른다. 다시 실패하면 더 뒤로 밀린다")
	void serverErrorBacksOff() {
		long alice = rows.newUser("s13-503");
		TestStudy study = rows.standardStudy(alice);
		String endpoint = WebPushStub.newEndpoint();
		WebPushStub.reply(endpoint, 503);
		notifications.insertSubscription(alice, endpoint, PushReceiver.create());
		long id = sharePrompt(study.studyId(), alice);
		Instant start = seoul(NOV_20, 22, 0);

		dispatcher.run();

		NotificationRow first = notifications.byId(id);
		assertThat(first.status()).isEqualTo("PENDING");
		assertThat(first.attempts()).isEqualTo(1);
		assertThat(first.lastError()).isEqualTo("HTTP_503");
		assertThat(first.nextAttemptAt()).isAfter(start);
		Duration firstDelay = Duration.between(start, first.nextAttemptAt());

		clock.setInstant(first.nextAttemptAt());
		dispatcher.run();

		NotificationRow second = notifications.byId(id);
		assertThat(second.status()).isEqualTo("PENDING");
		assertThat(second.attempts()).isEqualTo(2);
		assertThat(Duration.between(first.nextAttemptAt(), second.nextAttemptAt())).isGreaterThan(firstDelay);
		assertThat(WebPushStub.requestsTo(endpoint)).hasSize(2);
	}

	@Test
	@DisplayName("구독이 없으면 보내지 않고 FAILED다")
	void noSubscriptionFails() {
		long alice = rows.newUser("s13-no-sub");
		TestStudy study = rows.standardStudy(alice);
		long id = sharePrompt(study.studyId(), alice);

		dispatcher.run();

		NotificationRow row = notifications.byId(id);
		assertThat(row.status()).isEqualTo("FAILED");
		assertThat(row.lastError()).isEqualTo("NO_SUBSCRIPTION");
	}

	@Test
	@DisplayName("기기가 둘이면 둘 다에 보낸다. 하나라도 받으면 SENT이고, 410을 준 구독만 지운다")
	void sendsToEveryDeviceAndRemovesOnlyGoneOnes() {
		long alice = rows.newUser("s13-two");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), rows.newUser("s13-two-other"), NOV_1);
		String phone = WebPushStub.newEndpoint();
		String laptop = WebPushStub.newEndpoint();
		WebPushStub.reply(phone, 201);
		WebPushStub.reply(laptop, 410);
		long phoneSubscription = notifications.insertSubscription(alice, phone, PushReceiver.create());
		long laptopSubscription = notifications.insertSubscription(alice, laptop, PushReceiver.create());
		long id = sharePrompt(study.studyId(), alice);

		dispatcher.run();

		assertThat(notifications.byId(id).status()).isEqualTo("SENT");
		assertThat(notifications.subscription(phoneSubscription)).isPresent();
		assertThat(notifications.subscription(laptopSubscription)).isEmpty();
	}

	@Test
	@DisplayName("다른 4xx(예: 403)는 다시 보내도 같아서 재시도하지 않고 FAILED다. 구독은 지우지 않는다")
	void otherClientErrorFailsWithoutDeleting() {
		long alice = rows.newUser("s13-403");
		TestStudy study = rows.standardStudy(alice);
		String endpoint = WebPushStub.newEndpoint();
		WebPushStub.reply(endpoint, 403);
		long subscription = notifications.insertSubscription(alice, endpoint, PushReceiver.create());
		long id = sharePrompt(study.studyId(), alice);

		dispatcher.run();

		NotificationRow row = notifications.byId(id);
		assertThat(row.status()).isEqualTo("FAILED");
		assertThat(row.lastError()).isEqualTo("HTTP_403");
		assertThat(notifications.subscription(subscription)).isPresent();
	}

	private double counter(String name, String... tags) {
		var counter = meterRegistry.find(name).tags(tags).counter();
		return counter == null ? 0 : counter.count();
	}

}
