package com.duskafka.studybot.notification.outbox;

import com.duskafka.studybot.notification.persistence.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 outbox에 행을 넣는다 (5.8 '스케줄러(생성)와 발송기(전송)를 분리'). 알림을 만드는 쪽(마감 전 알림, 규칙 변경, S14의 결과 알림)은 이것만
 * 부른다. 보내는 일은 발송기가 따로 한다.
 *
 * <p>같은 {@code dedupe_key}는 한 번만 들어간다 (5.8 '중복 생성 금지', T15). 부르는 쪽 트랜잭션 안에서만 부른다. 알림을 만드는 일(진행 지점
 * 올리기, 규칙 변경)과 같은 트랜잭션에 들어가야 둘 중 하나만 남는 일이 없다.
 *
 * <p>지표: {@code studybot.notification.created}(종류별로 새로 넣은 알림 수, {@code type} 태그).
 */
@Component
@RequiredArgsConstructor
public class NotificationOutbox {

	private final NotificationRepository notifications;

	private final JsonMapper jsonMapper;

	private final MeterRegistry meterRegistry;

	private final Clock clock;

	/**
	 * 웹 푸시 알림을 넣는다. 처음 시도 시각은 지금이다.
	 *
	 * @return 새로 넣었는지. 같은 키가 이미 있으면 거짓이다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean addWebPush(NewWebPush notification) {
		int inserted = this.notifications.insertIfAbsent(notification.type().name(), NotificationChannel.WEB_PUSH.name(),
				notification.studyId(), notification.userId(), notification.studyDay(), notification.dedupeKey(),
				this.jsonMapper.writeValueAsString(notification.message()), notification.expiresAt(),
				this.clock.instant());
		if (inserted == 1) {
			this.meterRegistry.counter("studybot.notification.created", "type", notification.type().name()).increment();
		}
		return inserted == 1;
	}

	/** 마감 전 알림의 키 (5.8 표). */
	public static String reminderKey(long studyId, long userId, LocalDate studyDay) {
		return "REMINDER:" + studyId + ":" + userId + ":" + studyDay;
	}

	/** 공유 유도의 키 (5.8 표). */
	public static String sharePromptKey(long studyId, LocalDate studyDay) {
		return "SHARE_PROMPT:" + studyId + ":" + studyDay;
	}

	/** 규칙 변경의 키 (5.8 표). 규칙 버전 ID는 바뀐 뒤의 마지막 버전이다. */
	public static String ruleChangedKey(long studyId, long ruleId, long userId) {
		return "RULE:" + studyId + ":" + ruleId + ":" + userId;
	}

	/**
	 * 예정된 규칙 변경 취소의 키. 규칙 버전 ID는 취소된 마지막 예정 버전이다. 그 변경을 알린 키({@link #ruleChangedKey})와 겹치지 않게
	 * {@code :cancelled}를 붙인다 (사람이 정함 2026-11-28 '취소도 알림', 5.8 표에 없는 형식이라 S13 PR 스펙 변경 제안).
	 */
	public static String ruleChangeCancelledKey(long studyId, long cancelledRuleId, long userId) {
		return ruleChangedKey(studyId, cancelledRuleId, userId) + ":cancelled";
	}

	/**
	 * 넣을 웹 푸시 알림.
	 *
	 * @param studyDay 알림이 가리키는 스터디 일. 날과 상관없으면 {@code null}
	 * @param expiresAt 이 시각이 지나면 보내지 않는다
	 */
	public record NewWebPush(NotificationType type, long studyId, long userId, @Nullable LocalDate studyDay,
			String dedupeKey, PushMessage message, Instant expiresAt) {

		public NewWebPush {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(dedupeKey, "dedupeKey");
			Objects.requireNonNull(message, "message");
			Objects.requireNonNull(expiresAt, "expiresAt");
		}

	}

}
