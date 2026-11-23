package com.duskafka.studybot.github.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * GitHub API를 쓰지 못함 (장애, 시간 초과, 거절) → 503 problem+json. 사용자는 잠시 뒤 다시 시도하면 된다.
 * 메시지·원인에 토큰·JWT·키를 싣지 않는다. GitHub 응답 본문도 싣지 않는다.
 */
public class GithubApiUnavailableException extends ErrorResponseException {

	static final String DETAIL = "GitHub에 잠시 연결할 수 없어요. 잠시 뒤 다시 시도해 주세요.";

	GithubApiUnavailableException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, DETAIL),
				null);
	}

}
