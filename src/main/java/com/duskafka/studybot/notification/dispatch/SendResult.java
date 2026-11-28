package com.duskafka.studybot.notification.dispatch;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** 알림 하나를 채널로 보낸 결과 (5.8 응답 처리: 201 → SENT, 404·410 → FAILED, 429·5xx → 재시도). */
public sealed interface SendResult {

	/** 받았다 (웹 푸시는 기기 하나 이상). */
	record Delivered() implements SendResult {
	}

	/**
	 * 다시 보내도 같아서 그만둔다.
	 *
	 * @param reason {@code last_error}에 남길 까닭 (예: {@code SUBSCRIPTION_GONE}, {@code HTTP_403})
	 */
	record Failed(String reason) implements SendResult {

		public Failed {
			Objects.requireNonNull(reason, "reason");
		}

	}

	/**
	 * 일시 실패라 나중에 다시 보낸다.
	 *
	 * @param retryAfter 서비스가 알려 준 대기 시간({@code Retry-After}). 없으면 발송기의 백오프를 쓴다
	 * @param reason {@code last_error}에 남길 까닭 (예: {@code HTTP_503})
	 */
	record Retry(@Nullable Duration retryAfter, String reason) implements SendResult {

		public Retry {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
