package com.duskafka.studybot.auth.session;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * 세션 쿠키 (api.md 4.5, security.md 9.7, 결정 제안서 002 ③)와 30일 롤링 필터.
 *
 * <p>쿠키 설정을 {@code server.servlet.session.cookie.*} 대신 이 빈에 둔다. Spring Boot 4.1은 내장 서버로 뜰 때만 그
 * 속성을 Spring Session 쿠키에 넘기고, MockMvc 테스트 같은 서블릿 컨텍스트 환경에서는 서블릿 컨텍스트의 기본값
 * (이름 {@code SESSION}, Secure 없음)을 쓴다 ({@code SessionAutoConfiguration}). 이 빈이 있으면 Boot의 기본 빈은
 * 만들어지지 않아서, 운영과 테스트가 같은 쿠키를 쓴다.
 */
@Configuration(proxyBeanMethods = false)
class SessionCookieConfiguration {

	/**
	 * {@code __Host-} 접두사는 Secure, Path=/, Domain 없음일 때만 브라우저가 받아 주고, 하위 도메인이 덮어쓰지 못한다.
	 * {@code __Host-Http-}는 여기에 HttpOnly(서버가 설정한 쿠키)까지 브라우저가 확인한다.
	 */
	static final String SESSION_COOKIE_NAME = "__Host-Http-SESSION";

	/** Spring Session 세션 필터({@link SessionRepositoryFilter#DEFAULT_ORDER}) 바로 뒤, Spring Security 필터보다 앞. */
	static final int ROLLING_COOKIE_FILTER_ORDER = SessionRepositoryFilter.DEFAULT_ORDER + 1;

	/**
	 * 쿠키 만료(Max-Age)는 세션 만료({@code spring.session.timeout}, 30일)와 같다.
	 * 세션이 있는 요청마다 {@link RollingSessionCookieFilter}가 다시 써서 쿠키도 함께 연장된다.
	 */
	@Bean
	DefaultCookieSerializer cookieSerializer(@Value("${spring.session.timeout}") Duration sessionTimeout) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(SESSION_COOKIE_NAME);
		serializer.setCookiePath("/");
		serializer.setUseSecureCookie(true);
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setCookieMaxAge(Math.toIntExact(sessionTimeout.toSeconds()));
		return serializer;
	}

	@Bean
	FilterRegistrationBean<RollingSessionCookieFilter> rollingSessionCookieFilter(CookieSerializer cookieSerializer) {
		FilterRegistrationBean<RollingSessionCookieFilter> registration = new FilterRegistrationBean<>(
				new RollingSessionCookieFilter(cookieSerializer));
		registration.setOrder(ROLLING_COOKIE_FILTER_ORDER);
		return registration;
	}

}
