package com.duskafka.studybot.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 현재 상태와 충돌함 → 409 problem+json.
 * {@code detail}은 그대로 응답에 실리므로, 사용자에게 보여도 되는 문장만 넣는다 (내부 식별자·쿼리·비밀값 금지).
 *
 * <p>화면이 409를 갈라 처리해야 하면 {@code code} 확장 속성(RFC 9457 3.2)에 고정 값(대문자 스네이크)을 담는다. 문장은 바뀔 수 있어도
 * 코드는 바꾸지 않는다 (사람이 정함, 2026-11-26 S10 PR #26 "code 속성 추가").
 */
public class ConflictException extends ErrorResponseException {

	/** 화면이 오류를 가를 때 쓰는 problem+json 확장 속성 이름. */
	public static final String CODE_PROPERTY = "code";

	public ConflictException(String detail) {
		super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail), null);
	}

	/**
	 * @param code 화면이 가를 고정 값 (예: {@code STUDY_DAY_JUDGED})
	 * @param detail 사용자에게 보여 줄 문장
	 */
	public ConflictException(String code, String detail) {
		this(detail);
		getBody().setProperty(CODE_PROPERTY, code);
	}

}
