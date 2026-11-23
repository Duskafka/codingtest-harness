package com.duskafka.studybot.github.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 수신함에 저장하지 못함 → 503 problem+json (5.3 응답 코드 규칙 'DB 장애로 저장 실패'). GitHub에 실패한 전달로 남아야
 * 나중에 재전송할 수 있다 [S34][S35].
 */
class WebhookStorageUnavailableException extends ErrorResponseException {

	WebhookStorageUnavailableException(Throwable cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE,
				ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "지금은 웹훅을 저장할 수 없어요"), cause);
	}

}
