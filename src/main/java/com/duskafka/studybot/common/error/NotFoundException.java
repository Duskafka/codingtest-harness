package com.duskafka.studybot.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 대상이 없음 → 404 problem+json.
 * {@code detail}은 그대로 응답에 실리므로, 사용자에게 보여도 되는 문장만 넣는다 (내부 식별자·쿼리·비밀값 금지).
 */
public class NotFoundException extends ErrorResponseException {

	public NotFoundException(String detail) {
		super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail), null);
	}

}
