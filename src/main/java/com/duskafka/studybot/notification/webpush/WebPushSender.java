package com.duskafka.studybot.notification.webpush;

import com.duskafka.studybot.notification.dispatch.NotificationSender;
import com.duskafka.studybot.notification.dispatch.SendResult;
import com.duskafka.studybot.notification.outbox.NotificationChannel;
import com.duskafka.studybot.notification.outbox.NotificationType;
import com.duskafka.studybot.notification.persistence.NotificationQueue.ClaimedNotification;
import com.duskafka.studybot.notification.persistence.PushSubscription;
import com.duskafka.studybot.notification.persistence.PushSubscriptionRepository;
import com.zerodeplibs.webpush.EncryptedPushMessage;
import com.zerodeplibs.webpush.MessageEncryption;
import com.zerodeplibs.webpush.MessageEncryptions;
import com.zerodeplibs.webpush.UserAgentMessageEncryptionKeyInfo;
import com.zerodeplibs.webpush.jwt.VAPIDJWTParam;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * 웹 푸시 발송 (S13 만들 것 6, 5.8, RFC 8030·8291·8292, OD-11 결정 제안서 009).
 *
 * <ul>
 * <li>VAPID 헤더 값과 암호문(aes128gcm)만 zerodep-web-push-java로 만든다. 요청은 이 클래스의 JDK {@link HttpClient}(리다이렉트 끔, 연결
 * 3초, 응답 10초)로 보내고 응답 본문은 읽지 않고 버린다 (9.7 '리다이렉트를 따라가지 않기, 응답 시간·크기 제한').</li>
 * <li>JWT 만료는 주입된 {@code Clock}의 지금 + 12시간이다 (RFC 8292: 24시간 안). 라이브러리의 {@code expiresAfter}는 시스템 시각을 읽어서
 * 쓰지 않는다 (결정 제안서 009 '추천과 이유').</li>
 * <li>TTL은 만료 시각까지 남은 초다. 기기가 꺼져 있다가 다음 날 켜져도 지난 알림이 뜨지 않는다 (5.8 'TTL을 기준 시각까지로').</li>
 * <li>{@code Topic}은 종류·스터디·날마다 하나다. 같은 날 알림은 새것이 옛것을 덮는다 (5.8). {@code Urgency}는 마감 전 알림만 높게 한다.</li>
 * <li>받는 사람의 구독(기기)마다 보낸다. 2xx면 받은 것, 404·410이면 구독을 지운다(T17), 429·5xx·연결 실패면 나중에 다시, 그 밖의 4xx는 다시
 * 보내도 같아서 그만둔다. 기기 하나라도 받으면 보낸 것이다.</li>
 * </ul>
 *
 * <p>로그에는 알림 ID·구독 ID·상태 코드만 남긴다. 구독 주소·키·payload는 남기지 않는다.
 *
 * <p>지표: {@code studybot.notification.subscriptions.removed}(404·410으로 지운 구독 수, 5.8 '구독 만료 정리 건수').
 */
@Slf4j
public class WebPushSender implements NotificationSender {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

	static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	/** VAPID JWT 수명. RFC 8292는 24시간을 넘지 말라고 한다. */
	static final Duration JWT_LIFETIME = Duration.ofHours(12);

	/** {@code Retry-After}를 이보다 길게 따르지 않는다. 그 전에 만료되면 발송기가 만료로 둔다. */
	static final Duration MAX_RETRY_AFTER = Duration.ofHours(1);

	private final HttpClient http;

	private final VapidKeys vapidKeys;

	private final PushSubscriptionRepository subscriptions;

	private final MessageEncryption encryption = MessageEncryptions.of();

	private final Counter removedCounter;

	WebPushSender(HttpClient http, VapidKeys vapidKeys, PushSubscriptionRepository subscriptions,
			MeterRegistry meterRegistry) {
		this.http = http;
		this.vapidKeys = vapidKeys;
		this.subscriptions = subscriptions;
		this.removedCounter = Counter.builder("studybot.notification.subscriptions.removed")
			.description("푸시 서비스가 404·410을 줘서 지운 구독 수")
			.register(meterRegistry);
	}

	static HttpClient httpClient() {
		return HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.proxy(java.net.ProxySelector.getDefault())
			.build();
	}

	@Override
	public NotificationChannel channel() {
		return NotificationChannel.WEB_PUSH;
	}

	@Override
	public SendResult send(ClaimedNotification notification, Instant now) {
		Long userId = notification.userId();
		if (userId == null) {
			return new SendResult.Failed("NO_RECIPIENT");
		}
		List<PushSubscription> targets = this.subscriptions.findByUserIdOrderById(userId);
		if (targets.isEmpty()) {
			return new SendResult.Failed("NO_SUBSCRIPTION");
		}
		byte[] payload = notification.payload().getBytes(StandardCharsets.UTF_8);
		long ttlSeconds = Math.max(1, Duration.between(now, notification.expiresAt()).toSeconds());
		boolean delivered = false;
		String failure = null;
		SendResult.Retry retry = null;
		for (PushSubscription subscription : targets) {
			Outcome outcome = sendTo(subscription, notification, payload, ttlSeconds, now);
			switch (outcome.kind()) {
				case DELIVERED -> {
					delivered = true;
					this.subscriptions.markSuccess(subscription.getId(), now);
				}
				case GONE -> {
					if (this.subscriptions.deleteGone(subscription.getId(), subscription.getP256dh()) == 1) {
						this.removedCounter.increment();
					}
				}
				case RETRY -> {
					this.subscriptions.markFailure(subscription.getId());
					retry = laterOf(retry, new SendResult.Retry(outcome.retryAfter(), outcome.reason()));
				}
				case FAILED -> {
					this.subscriptions.markFailure(subscription.getId());
					failure = failure == null ? outcome.reason() : failure;
				}
			}
		}
		if (delivered) {
			return new SendResult.Delivered();
		}
		if (retry != null) {
			return retry;
		}
		return new SendResult.Failed(failure == null ? "SUBSCRIPTION_GONE" : failure);
	}

	private Outcome sendTo(PushSubscription subscription, ClaimedNotification notification, byte[] payload,
			long ttlSeconds, Instant now) {
		HttpRequest request;
		try {
			request = request(subscription, notification, payload, ttlSeconds, now);
		}
		catch (RuntimeException ex) {
			// 저장된 키가 틀렸거나(등록 때 검사했지만) 암호화에 실패했다. 다시 보내도 같다.
			log.warn("웹 푸시 요청을 만들지 못했어요: notification={}, subscription={}, error={}", notification.id(),
					subscription.getId(), ex.getClass().getSimpleName());
			return new Outcome(Kind.FAILED, null, "INVALID_SUBSCRIPTION");
		}
		HttpResponse<Void> response;
		try {
			response = this.http.send(request, HttpResponse.BodyHandlers.discarding());
		}
		catch (IOException ex) {
			log.warn("웹 푸시를 보내지 못했어요: notification={}, subscription={}, error={}", notification.id(),
					subscription.getId(), ex.getClass().getSimpleName());
			return new Outcome(Kind.RETRY, null, "IO_ERROR");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return new Outcome(Kind.RETRY, null, "INTERRUPTED");
		}
		int status = response.statusCode();
		if (status >= 200 && status < 300) {
			return new Outcome(Kind.DELIVERED, null, null);
		}
		log.info("웹 푸시 서비스가 거절했어요: notification={}, subscription={}, status={}", notification.id(),
				subscription.getId(), status);
		if (status == 404 || status == 410) {
			return new Outcome(Kind.GONE, null, "SUBSCRIPTION_GONE");
		}
		if (status == 429 || status >= 500) {
			return new Outcome(Kind.RETRY, retryAfter(response, now).orElse(null), "HTTP_" + status);
		}
		return new Outcome(Kind.FAILED, null, "HTTP_" + status);
	}

	private HttpRequest request(PushSubscription subscription, ClaimedNotification notification, byte[] payload,
			long ttlSeconds, Instant now) {
		String authorization = this.vapidKeys.keyPair()
			.generateAuthorizationHeaderValue(VAPIDJWTParam.getBuilder()
				.resourceURLString(subscription.getEndpoint())
				.expirationTime(now.plus(JWT_LIFETIME))
				.subject(this.vapidKeys.subject())
				.build());
		EncryptedPushMessage encrypted = this.encryption.encrypt(
				UserAgentMessageEncryptionKeyInfo.of(subscription.getP256dh(), subscription.getAuth()),
				com.zerodeplibs.webpush.PushMessage.of(payload));
		return HttpRequest.newBuilder(URI.create(subscription.getEndpoint()))
			.timeout(READ_TIMEOUT)
			.header("Authorization", authorization)
			.header("Content-Encoding", encrypted.contentEncoding())
			.header("Content-Type", encrypted.mediaType())
			.header("TTL", Long.toString(ttlSeconds))
			.header("Urgency", urgencyOf(notification.type()))
			.header("Topic", topicOf(notification))
			.POST(HttpRequest.BodyPublishers.ofByteArray(encrypted.toBytes()))
			.build();
	}

	/** 마감 전 알림은 기기가 절전 중이어도 받게 높게, 나머지는 보통 (RFC 8030 5.3). */
	static String urgencyOf(String type) {
		return NotificationType.REMINDER.name().equals(type) ? "high" : "normal";
	}

	/**
	 * 같은 Topic의 아직 전달되지 않은 메시지는 새것이 덮는다 (RFC 8030 5.4: URL-safe base64 문자 32자까지). 종류·스터디·날로 나눈다. 날이 없는
	 * 알림(규칙 변경)은 종류·스터디로 나눈다.
	 */
	static String topicOf(ClaimedNotification notification) {
		String prefix = switch (notification.type()) {
			case "REMINDER" -> "r";
			case "SHARE_PROMPT" -> "s";
			case "RULE_CHANGED" -> "c";
			default -> "n";
		};
		String topic = prefix + notification.studyId();
		if (notification.studyDay() != null) {
			topic += "d" + notification.studyDay().format(DateTimeFormatter.BASIC_ISO_DATE);
		}
		return topic.length() <= 32 ? topic : topic.substring(0, 32);
	}

	/** {@code Retry-After}: 초 또는 HTTP 날짜 (RFC 9110 10.2.3). 읽지 못하면 빈 값이다. 1초~1시간으로 자른다. */
	static Optional<Duration> retryAfter(HttpResponse<?> response, Instant now) {
		return response.headers().firstValue("Retry-After").flatMap(value -> parseRetryAfter(value.strip(), now))
			.map(duration -> duration.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1)
					: duration.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : duration);
	}

	private static Optional<Duration> parseRetryAfter(String value, Instant now) {
		if (value.chars().allMatch(Character::isDigit) && !value.isEmpty() && value.length() <= 9) {
			return Optional.of(Duration.ofSeconds(Long.parseLong(value)));
		}
		try {
			Instant at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
			return Optional.of(Duration.between(now, at));
		}
		catch (DateTimeParseException ex) {
			return Optional.empty();
		}
	}

	private static SendResult.Retry laterOf(SendResult.@Nullable Retry current, SendResult.Retry next) {
		if (current == null) {
			return next;
		}
		if (current.retryAfter() == null) {
			return next.retryAfter() == null ? current : next;
		}
		if (next.retryAfter() == null) {
			return current;
		}
		return next.retryAfter().compareTo(current.retryAfter()) > 0 ? next : current;
	}

	private enum Kind {

		DELIVERED, GONE, RETRY, FAILED

	}

	private record Outcome(Kind kind, @Nullable Duration retryAfter, @Nullable String reason) {

		@Override
		public String reason() {
			return this.reason == null ? "" : this.reason;
		}

	}

}
