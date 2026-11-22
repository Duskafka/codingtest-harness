package com.duskafka.studybot.auth.security;

import com.duskafka.studybot.common.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 로그인이 필요한 요청에 로그인하지 않았으면 401 {@code application/problem+json}으로 답한다 (S04 만들 것 7).
 *
 * <p>로그인 페이지로 리다이렉트하지 않는다. 로그인 화면은 프론트(SPA)가 맡는다.
 * 응답 모양을 S01 공통 오류 형식과 같게 하려고, 공통 예외를 MVC 예외 처리({@code GlobalExceptionHandler})로 넘긴다.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

	static final String DETAIL = "로그인이 필요해요.";

	private final HandlerExceptionResolver exceptionResolver;

	public ProblemDetailAuthenticationEntryPoint(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) {
		this.exceptionResolver.resolveException(request, response, null, new UnauthorizedException(DETAIL));
	}

}
