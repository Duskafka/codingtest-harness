package com.duskafka.studybot.notification.discord;

import com.duskafka.studybot.common.error.ConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 디스코드에 지금 보낼 수 없음 (429, 5xx, 연결 실패) → 503 problem+json, {@code code} {@value #CODE}. 잠시 뒤 다시 시도하면 된다. 응답
 * 본문·웹훅 주소를 싣지 않는다.
 */
public class DiscordUnavailableException extends ErrorResponseException {

	static final String CODE = "DISCORD_UNAVAILABLE";

	static final String DETAIL = "디스코드에 잠시 보낼 수 없어요. 잠시 뒤 다시 시도해 주세요.";

	DiscordUnavailableException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, DETAIL),
				null);
		getBody().setProperty(ConflictException.CODE_PROPERTY, CODE);
	}

}
