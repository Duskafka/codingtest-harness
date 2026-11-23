package com.duskafka.studybot.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 더는 쓸 수 없음 (예: 만료·철회·소진된 초대) → 410 problem+json.
 * {@code detail}은 그대로 응답에 실리므로, 사용자에게 보여도 되는 문장만 넣는다 (내부 식별자·쿼리·비밀값 금지).
 */
public class GoneException extends ErrorResponseException {

	public GoneException(String detail) {
		super(HttpStatus.GONE, ProblemDetail.forStatusAndDetail(HttpStatus.GONE, detail), null);
	}

}
