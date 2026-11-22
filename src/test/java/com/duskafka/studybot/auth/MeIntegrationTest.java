package com.duskafka.studybot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 내 정보 {@code GET /api/me}와 인증 실패 응답 (S04 만들 것 5·7, api.md 4.4, T19 기반).
 * 연결된 레포(S07)와 알림 구독 상태(S13)는 그 단계에서 응답에 더한다.
 */
class MeIntegrationTest extends LoginTestSupport {

	@Test
	@DisplayName("S04 (T19 기반): 로그인하지 않고 GET /api/me → 401 application/problem+json")
	void anonymousMeReturns401Problem() {
		MvcTestResult result = mvc.get().uri("/api/me").exchange();

		assertUnauthorizedProblem(result);
	}

	@Test
	@DisplayName("S04 (T19 기반): 로그인하지 않고 없는 /api 경로 → 401 problem+json (어떤 엔드포인트가 있는지 드러내지 않는다)")
	void anonymousUnknownApiPathReturns401Problem() {
		MvcTestResult result = mvc.get().uri("/api/does-not-exist").exchange();

		assertUnauthorizedProblem(result);
	}

	@Test
	@DisplayName("S04: 로그인하면 GET /api/me가 내 정보(id, githubLogin, avatarUrl)를 준다")
	void loggedInMeReturnsProfile() {
		Login login = login(6101L, "me-user", "https://avatars.example.com/u/6101");

		MvcTestResult result = withSession(mvc.get().uri("/api/me"), login.session()).exchange();

		assertThat(result).hasStatus(HttpStatus.OK).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
		assertThat(result).bodyJson().extractingPath("$.id").convertTo(Long.class).isEqualTo(userIdOf(6101L));
		assertThat(result).bodyJson().extractingPath("$.githubLogin").isEqualTo("me-user");
		assertThat(result).bodyJson().extractingPath("$.avatarUrl").isEqualTo("https://avatars.example.com/u/6101");
		assertThat(result).bodyJson().doesNotHavePath("$.githubId");
		assertThat(result).bodyJson().doesNotHavePath("$.email");
	}

	@Test
	@DisplayName("S04 (T19 기반): 두 사용자가 로그인하면 각자 자기 정보만 받는다")
	void eachUserSeesOnlyOwnProfile() {
		Login first = login(6201L, "me-first", "https://avatars.example.com/u/6201");
		Login second = login(6202L, "me-second", "https://avatars.example.com/u/6202");

		assertThat(withSession(mvc.get().uri("/api/me"), first.session())).bodyJson()
			.extractingPath("$.githubLogin")
			.isEqualTo("me-first");
		assertThat(withSession(mvc.get().uri("/api/me"), second.session())).bodyJson()
			.extractingPath("$.githubLogin")
			.isEqualTo("me-second");
	}

	private static void assertUnauthorizedProblem(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(401);
		assertThat(result).bodyJson().extractingPath("$.detail").asString().isNotBlank();
		assertThat(result).bodyText().doesNotContain("Exception").doesNotContain("at org.");
	}

}
