package com.duskafka.studybot.auth.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션이 있는 요청마다 세션 쿠키를 다시 내려 줘서, 브라우저 쪽 쿠키 만료(Max-Age 30일)도 서버 세션처럼 굴러가게 한다
 * (30일 롤링, api.md 4.5, 결정 제안서 002 ② a).
 *
 * <p>Spring Session은 세션이 새로 생기거나 ID가 바뀔 때만 쿠키를 쓴다. 그래서 Max-Age만 정해 두면 서버 세션은
 * 요청마다 연장되는데 쿠키는 로그인한 날로부터 30일에 끝나, 실제로는 '로그인 뒤 30일 절대 만료'가 된다.
 * 이 필터는 Spring Session의 세션 필터 바로 뒤에서 돌며, 요청에 유효한 세션이 있으면 같은 값의 쿠키를 다시 쓴다.
 * 쿠키 속성(이름, Secure, HttpOnly, SameSite, Max-Age)은 Spring Session과 같은 {@link CookieSerializer}가 정한다.
 *
 * <p>같은 요청에서 세션 ID가 바뀌거나(로그인) 세션이 무효가 되면(로그아웃) Spring Session이 나중에 쿠키를 한 번 더 쓰고,
 * 브라우저는 마지막 Set-Cookie를 따른다.
 */
public class RollingSessionCookieFilter extends OncePerRequestFilter {

	private final CookieSerializer cookieSerializer;

	public RollingSessionCookieFilter(CookieSerializer cookieSerializer) {
		this.cookieSerializer = cookieSerializer;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null) {
			this.cookieSerializer.writeCookieValue(new CookieValue(request, response, session.getId()));
		}
		filterChain.doFilter(request, response);
	}

}
