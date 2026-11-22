package com.duskafka.studybot.auth.security;

import com.duskafka.studybot.common.error.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 권한이 없거나 CSRF 토큰이 없거나 틀리면 403 {@code application/problem+json}으로 답한다 (S04 만들 것 4·7).
 * 응답 모양은 S01 공통 오류 형식과 같다 ({@link ProblemDetailAuthenticationEntryPoint} 참고).
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

	static final String CSRF_DETAIL = "요청을 확인할 수 없어요. 페이지를 새로 고친 뒤 다시 시도해 주세요.";

	static final String DETAIL = "이 요청을 할 권한이 없어요.";

	private final HandlerExceptionResolver exceptionResolver;

	public ProblemDetailAccessDeniedHandler(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) {
		String detail = (accessDeniedException instanceof CsrfException) ? CSRF_DETAIL : DETAIL;
		this.exceptionResolver.resolveException(request, response, null, new ForbiddenException(detail));
	}

}
