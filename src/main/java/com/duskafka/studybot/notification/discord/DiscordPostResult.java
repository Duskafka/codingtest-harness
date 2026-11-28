package com.duskafka.studybot.notification.discord;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** 디스코드 웹훅에 한 번 게시한 결과 (5.8 '디스코드', S14 만들 것 4). */
public sealed interface DiscordPostResult {

	/** 받았다 (2xx). */
	record Delivered() implements DiscordPostResult {
	}

	/** 웹훅이 지워졌다 (404). 끄고 방장에게 알린다. */
	record Gone() implements DiscordPostResult {
	}

	/**
	 * 일시 실패라 나중에 다시 보낸다 (429, 5xx, 연결 실패).
	 *
	 * @param retryAfter 디스코드가 알려 준 대기 시간 (429). 없으면 발송기의 백오프를 쓴다
	 * @param reason 실패 까닭 (예: {@code HTTP_429})
	 */
	record Retry(@Nullable Duration retryAfter, String reason) implements DiscordPostResult {

		public Retry {
			Objects.requireNonNull(reason, "reason");
		}

	}

	/**
	 * 다시 보내도 같은 실패 (그 밖의 4xx, 리다이렉트).
	 *
	 * @param reason 실패 까닭 (예: {@code HTTP_401})
	 */
	record Rejected(String reason) implements DiscordPostResult {

		public Rejected {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
