package com.duskafka.studybot.github.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 웹훅 수신함의 한 행 (data-model.md 4.3 {@code webhook_inbox}, 5.3).
 *
 * <p>행은 {@link WebhookInboxRepository#insertIfAbsent}가 한 문장으로 만든다 (같은 전달이 동시에 와도 한 행만 남게).
 * 이 엔티티는 처리 결과를 남길 때만 쓴다.
 *
 * <p>{@code payload} 컬럼은 매핑하지 않는다. 처리기는 요청에서 읽은 본문을 받고, 엔티티로 payload를 읽거나 쓸 일이 없다.
 * JSONB를 엔티티에 매핑하면 Hibernate의 JSON 변환기(Jackson 버전)에 기대게 된다 (S06 PR 판단 기록).
 */
@Entity
@Table(name = "webhook_inbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookInbox {

	/** {@code last_error}에 남기는 최대 글자 수. 긴 메시지는 잘라서 남긴다. */
	public static final int LAST_ERROR_MAX_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, updatable = false)
	private WebhookSource source;

	@Column(name = "delivery_id", nullable = false, updatable = false)
	private String deliveryId;

	/** 이벤트 이름. GitHub는 {@code X-GitHub-Event} 값 그대로다 (예: {@code push}). */
	@Column(name = "event_type", nullable = false, updatable = false)
	private String eventType;

	/** 받은 시각. push의 풀이 인정 시각이다 (D5, 5.3 'push 처리 순서' 6). */
	@Column(name = "received_at", nullable = false, updatable = false)
	private Instant receivedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private WebhookInboxStatus status;

	/** 처리를 시도한 횟수. P1은 받은 요청 안에서 한 번 처리한다. */
	@Column(name = "attempts", nullable = false)
	private int attempts;

	/** 다음 재시도 시각. P2 워커가 쓴다. */
	@Column(name = "next_attempt_at")
	private @Nullable Instant nextAttemptAt;

	@Column(name = "last_error")
	private @Nullable String lastError;

	/** 처리를 마친 시각 (PROCESSED, IGNORED). 실패하면 비어 있다. */
	@Column(name = "processed_at")
	private @Nullable Instant processedAt;

	public void markProcessed(Instant at) {
		finish(WebhookInboxStatus.PROCESSED, Objects.requireNonNull(at, "at"), null);
	}

	public void markIgnored(Instant at) {
		finish(WebhookInboxStatus.IGNORED, Objects.requireNonNull(at, "at"), null);
	}

	/** 처리에 실패했다. 오류 내용은 {@link #LAST_ERROR_MAX_LENGTH}자까지 남긴다. */
	public void markFailed(String error) {
		Objects.requireNonNull(error, "error");
		finish(WebhookInboxStatus.FAILED, null,
				error.length() > LAST_ERROR_MAX_LENGTH ? error.substring(0, LAST_ERROR_MAX_LENGTH) : error);
	}

	/** P1은 받은 직후(RECEIVED)에 한 번만 결과를 남긴다. 다른 상태에서 다시 부르면 버그다. */
	private void finish(WebhookInboxStatus result, @Nullable Instant at, @Nullable String error) {
		if (this.status != WebhookInboxStatus.RECEIVED) {
			throw new IllegalStateException("처리 결과는 RECEIVED 상태에서만 남길 수 있어요: " + this.status);
		}
		this.status = result;
		this.attempts++;
		this.processedAt = at;
		this.lastError = error;
	}

}
