package com.duskafka.studybot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

	@Test
	@DisplayName("S04: Spring Security가 만드는 기본 로그인 페이지(/login)를 내주지 않는다 (로그인 화면은 프론트)")
	void defaultLoginPageIsNotServed() {
		MvcTestResult result = mvc.get().uri("/login").exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

}
