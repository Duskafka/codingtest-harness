package com.duskafka.studybot.github.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 본문이 한도보다 큼 → 413 problem+json (S06 만들 것 7, 5.3 '주의할 것'). */
class WebhookBodyTooLargeException extends ErrorResponseException {

	WebhookBodyTooLargeException() {
		super(HttpStatus.CONTENT_TOO_LARGE,
				ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "요청 본문이 너무 커요"), null);
	}

}
