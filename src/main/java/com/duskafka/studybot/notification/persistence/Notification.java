package com.duskafka.studybot.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 알림 outbox 한 행 (data-model.md 4.3 {@code notifications}, glossary {@code Notification}, 5.8). 행은
 * {@link NotificationRepository}의 네이티브 쿼리로만 넣고 바꾼다. 이 엔티티는 스키마 확인({@code ddl-auto=validate})과 읽기용이다.
 *
 * <p>{@code payload}(JSONB)는 매핑하지 않는다. JSONB를 엔티티에 매핑하면 Hibernate의 JSON 변환기(Jackson 버전)에 기대게 된다
 * ({@code WebhookInbox}와 같은 이유, S06 PR 판단 기록). 발송기는 네이티브 쿼리로 문자열로 읽는다.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 알림 종류 ({@code NotificationType} 이름, 5.8 '알림 종류'). */
	@Column(name = "type", nullable = false, updatable = false)
	private String type;

	/** 채널 ({@code NotificationChannel} 이름). */
	@Column(name = "channel", nullable = false, updatable = false)
	private String channel;

	@Column(name = "study_id", nullable = false, updatable = false)
	private Long studyId;

	/** 받는 사람. 웹 푸시는 늘 있다(CHECK). 스터디 채널(디스코드, S14)은 비어 있을 수 있다. */
	@Column(name = "user_id", updatable = false)
	private @Nullable Long userId;

	/** 알림이 가리키는 스터디 일. 규칙 변경처럼 날과 상관없는 알림은 비어 있다. */
	@Column(name = "study_day", updatable = false)
	private @Nullable LocalDate studyDay;

	@Column(name = "dedupe_key", nullable = false, updatable = false)
	private String dedupeKey;

	/** 상태 ({@code NotificationStatus} 이름). */
	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "attempts", nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	/** 발송 중(SENDING)일 때만 있다. 발송기 한 번의 소유 표시다. */
	@Column(name = "locked_until")
	private @Nullable Instant lockedUntil;

	@Column(name = "sent_at")
	private @Nullable Instant sentAt;

	/** 이 시각이 지나면 보내지 않는다 (EXPIRED). 마감 전 알림은 그날 마감이다 (5.8). */
	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	/** 마지막 실패의 까닭 (예: {@code HTTP_503}, {@code SUBSCRIPTION_GONE}). 응답 본문이나 예외 메시지는 남기지 않는다. */
	@Column(name = "last_error")
	private @Nullable String lastError;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

}
