package com.duskafka.studybot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * CSRF (S04 만들 것 4, api.md 4.5 '주의할 것'). Spring Security의 SPA용 공식 설정({@code csrf.spa()}):
 * 토큰을 XSRF-TOKEN 쿠키로 내려 주고, SPA가 X-XSRF-TOKEN 헤더로 돌려보낸다.
 */
class CsrfIntegrationTest extends LoginTestSupport {

	@Test
	@DisplayName("S04 (T19 기반): 로그인한 사용자가 CSRF 토큰 없이 POST → 403 problem+json")
	void postWithoutCsrfTokenIsForbidden() {
		Login login = login(7101L, "csrf-missing", "https://avatars.example.com/u/7101");

		MvcTestResult result = withSession(mvc.post().uri("/api/test/csrf"), login.session()).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(403);
	}

	@Test
	@DisplayName("S04: CSRF 토큰 없는 로그아웃 요청도 403이고 세션은 그대로다")
	void logoutWithoutCsrfTokenIsForbidden() {
		Login login = login(7201L, "csrf-logout", "https://avatars.example.com/u/7201");

		assertThat(withSession(mvc.post().uri("/logout"), login.session())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(withSession(mvc.get().uri("/api/me"), login.session())).hasStatus(HttpStatus.OK);
	}

	@Test
	@DisplayName("S04: X-XSRF-TOKEN 헤더가 XSRF-TOKEN 쿠키와 맞으면 POST가 컨트롤러까지 간다")
	void postWithMatchingHeaderPasses() {
		Login login = login(7301L, "csrf-ok", "https://avatars.example.com/u/7301");
		String csrf = csrfTokenFor(login);

		MvcTestResult result = withSession(mvc.post().uri("/api/test/csrf"), login.session())
			.cookie(new Cookie(XSRF_COOKIE, csrf))
			.header(XSRF_HEADER, csrf)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.result").isEqualTo("reached");
	}

	@Test
	@DisplayName("S04: X-XSRF-TOKEN 헤더가 쿠키와 다르면 403")
	void postWithMismatchedHeaderIsForbidden() {
		Login login = login(7401L, "csrf-mismatch", "https://avatars.example.com/u/7401");
		String csrf = csrfTokenFor(login);

		MvcTestResult result = withSession(mvc.post().uri("/api/test/csrf"), login.session())
			.cookie(new Cookie(XSRF_COOKIE, csrf))
			.header(XSRF_HEADER, csrf + "-tampered")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

}
