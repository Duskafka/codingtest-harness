package com.duskafka.studybot.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 웹 푸시 구독 한 행: 기기(브라우저) 하나 (data-model.md 4.3 {@code push_subscriptions}, glossary {@code PushSubscription}). 행은
 * {@link PushSubscriptionRepository}의 네이티브 쿼리로 넣고 바꾼다. 이 엔티티는 스키마 확인과 읽기용이다.
 *
 * <p>{@code endpoint}는 푸시 서비스가 준 기기 주소이고 비밀 주소처럼 다룬다. 로그에 남기지 않는다.
 */
@Entity
@Table(name = "push_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "endpoint", nullable = false, updatable = false)
	private String endpoint;

	/** 브라우저가 준 P-256 공개키 (base64url, RFC 8291 {@code ua_public}). */
	@Column(name = "p256dh", nullable = false)
	private String p256dh;

	/** 브라우저가 준 인증 비밀 (base64url 16바이트, RFC 8291 {@code auth_secret}). */
	@Column(name = "auth", nullable = false)
	private String auth;

	@Column(name = "user_agent")
	private @Nullable String userAgent;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "last_success_at")
	private @Nullable Instant lastSuccessAt;

	/** 마지막 성공 뒤 연달아 실패한 횟수 (429·5xx·그 밖의 4xx). 성공하면 0이다. */
	@Column(name = "failure_count", nullable = false)
	private int failureCount;

}
