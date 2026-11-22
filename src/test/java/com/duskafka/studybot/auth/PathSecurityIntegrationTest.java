package com.duskafka.studybot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 경로별 보안 (S04 만들 것 6, api.md 4.5 '주의할 것').
 * 웹훅 경로는 CSRF와 세션에서 빼고 서명으로만 인증한다. 서명 검증은 S06이 붙인다.
 */
class PathSecurityIntegrationTest extends LoginTestSupport {

	@Test
	@DisplayName("S04: /webhooks/**는 CSRF 토큰과 로그인 없이 POST가 컨트롤러까지 간다")
	void webhookPostReachesControllerWithoutCsrf() {
		MvcTestResult result = mvc.post().uri("/webhooks/test").content("{}").exchange();

		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
	}

	@Test
	@DisplayName("S04: /webhooks/**는 세션을 만들지 않는다 (세션·CSRF 쿠키가 없다)")
	void webhookDoesNotCreateSession() {
		MvcTestResult result = mvc.post().uri("/webhooks/test").content("{}").exchange();

		assertThat(setCookieHeader(result, SESSION_COOKIE)).isEmpty();
		assertThat(setCookieHeader(result, XSRF_COOKIE)).isEmpty();
	}

	@Test
	@DisplayName("S04: /actuator/health는 로그인 없이 볼 수 있다")
	void healthIsPublic() {
		assertThat(mvc.get().uri("/actuator/health")).hasStatus(HttpStatus.OK);
	}

	/**
	 * S01의 {@code ActuatorExposureIntegrationTest}는 CSRF 토큰 없이 POST를 보내 4xx를 확인한다. S04부터는 그 요청이
	 * CSRF 검사(403)에서 먼저 막혀서 엔드포인트가 노출됐는지 알 수 없다. 그래서 CSRF 토큰을 붙여 한 번 더 본다.
	 * 404로 좁힌다. GET만 받는 엔드포인트가 노출되면 POST는 405가 되므로, '4xx'로는 노출을 가려내지 못한다.
	 */
	@ParameterizedTest(name = "S04: CSRF 토큰을 붙여도 POST /actuator/{0}는 노출되지 않는다 (404)")
	@ValueSource(strings = { "env", "heapdump", "configprops", "beans", "mappings", "loggers", "info", "metrics",
			"threaddump", "shutdown" })
	void actuatorPostIsNotExposedEvenWithCsrfToken(String endpoint) {
		String csrf = anonymousCsrfToken();

		MvcTestResult result = mvc.post()
			.uri("/actuator/" + endpoint)
			.cookie(new Cookie(XSRF_COOKIE, csrf))
			.header(XSRF_HEADER, csrf)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("S04: 로그인하지 않았어도 CSRF 토큰이 맞으면 POST /logout은 204다 (이미 끊긴 세션에서 눌러도 오류가 아니다)")
	void logoutWithoutLoginReturns204() {
		String csrf = anonymousCsrfToken();

		assertThat(mvc.post().uri("/logout").cookie(new Cookie(XSRF_COOKIE, csrf)).header(XSRF_HEADER, csrf))
			.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	@DisplayName("S04: Spring Security가 만드는 기본 로그인 페이지(/login)를 내주지 않는다 (로그인 화면은 프론트)")
	void defaultLoginPageIsNotServed() {
		MvcTestResult result = mvc.get().uri("/login").exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

}
