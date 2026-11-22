package com.duskafka.studybot.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * GitHub 로그인 (S04 만들 것 2, api.md 4.5, 결정 제안서 005).
 * GitHub App의 사용자 인증으로 신원을 확인하고, users를 github_id 기준으로 저장하거나 갱신한다.
 * GitHub access token은 신원 확인에만 쓰고 어디에도 저장하지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoginIntegrationTest extends LoginTestSupport {

	@Autowired
	private OAuth2AuthorizedClientService authorizedClientService;

	@Test
	@DisplayName("S04: 인가 요청은 GitHub 인증 페이지로 보내고 state와 PKCE(code_challenge, S256)를 붙인다")
	void authorizationRequestRedirectsToGithubWithStateAndPkce() {
		MvcTestResult result = startAuthorization();

		String location = result.getResponse().getHeader("Location");
		assertThat(location).startsWith(GithubOAuthStub.baseUrl() + GithubOAuthStub.AUTHORIZE_PATH);
		Map<String, String> params = queryParams(location);
		assertThat(params).containsEntry("client_id", "test-client-id")
			.containsEntry("response_type", "code")
			.containsEntry("code_challenge_method", "S256");
		assertThat(params.get("state")).isNotBlank();
		assertThat(params.get("code_challenge")).isNotBlank();
		assertThat(params.get("redirect_uri")).endsWith(CALLBACK_PATH);
	}

	@Test
	@DisplayName("S04: 콜백에서 code를 토큰으로 바꿀 때 PKCE code_verifier를 보낸다")
	void tokenRequestSendsCodeVerifier() {
		login(4101L, "pkce-user", "https://avatars.example.com/u/4101");

		GithubOAuthStub.server()
			.verify(postRequestedFor(urlPathEqualTo(GithubOAuthStub.TOKEN_PATH)).withRequestBody(
					containing("code_verifier=")));
	}

	@Test
	@DisplayName("S04: 로그인 콜백을 거치면 users 행이 생긴다 (github_id·login·avatar, 가입 시각은 주입된 Clock)")
	void callbackCreatesUser() {
		Instant now = Instant.parse("2026-11-22T03:00:00Z");
		clock.setInstant(now);

		login(4201L, "octo-new", "https://avatars.example.com/u/4201");

		Map<String, Object> row = userRow(4201L);
		assertThat(row).containsEntry("github_login", "octo-new")
			.containsEntry("avatar_url", "https://avatars.example.com/u/4201");
		assertThat(toInstant(row.get("created_at"))).isEqualTo(now);
		assertThat(row.get("deleted_at")).isNull();
	}

	@Test
	@DisplayName("S04: 같은 github_id로 다시 로그인하면 행이 늘지 않고 login·avatar만 갱신된다 (가입 시각은 그대로)")
	void reloginUpdatesExistingUser() {
		Instant firstLogin = Instant.parse("2026-11-22T03:00:00Z");
		clock.setInstant(firstLogin);
		login(4301L, "octo-before", "https://avatars.example.com/u/4301?v=1");
		long usersBefore = countUsers();

		clock.advance(Duration.ofDays(3));
		login(4301L, "octo-after", "https://avatars.example.com/u/4301?v=2");

		assertThat(countUsers()).isEqualTo(usersBefore);
		Map<String, Object> row = userRow(4301L);
		assertThat(row).containsEntry("github_login", "octo-after")
			.containsEntry("avatar_url", "https://avatars.example.com/u/4301?v=2");
		assertThat(toInstant(row.get("created_at"))).isEqualTo(firstLogin);
	}

	@Test
	@DisplayName("S04: 로그인에 성공하면 홈(/)으로 보내고 세션 쿠키와 XSRF-TOKEN 쿠키를 내려 준다")
	void successfulLoginRedirectsHomeWithCookies() {
		Login login = login(4401L, "octo-home", "https://avatars.example.com/u/4401");

		assertThat(login.callback()).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/");
		assertThat(cookieValue(login.callback(), SESSION_COOKIE)).isPresent();
		assertThat(login.xsrfToken()).isNotBlank();
	}

	@Test
	@DisplayName("S04: 로그인하면 세션 ID가 바뀐다 (세션 고정 방지)")
	void sessionIdChangesOnLogin() {
		Login login = login(4501L, "octo-fixation", "https://avatars.example.com/u/4501");

		assertThat(login.session()).isNotEqualTo(login.preLoginSession());
		MvcTestResult withOldSession = withSession(mvc.get().uri("/api/me"), login.preLoginSession()).exchange();
		assertThat(withOldSession).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("S04: GitHub가 사용자 정보를 주지 않으면 로그인에 실패하고 users 행이 생기지 않는다")
	void loginFailsWhenGithubRejects() {
		long usersBefore = countUsers();
		GithubOAuthStub.stubUserEndpointFailure();

		Login login = completeLogin();

		assertThat(login.callback()).hasStatus(HttpStatus.FOUND).hasRedirectedUrl("/?login_error");
		assertThat(countUsers()).isEqualTo(usersBefore);
		assertThat(withSession(mvc.get().uri("/api/me"), login.session())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("S04: GitHub access token·refresh token이 DB와 로그 어디에도 남지 않는다")
	void githubTokensAreNotPersisted(CapturedOutput output) {
		Login login = login(4601L, "octo-token", "https://avatars.example.com/u/4601");
		assertThat(withSession(mvc.get().uri("/api/me"), login.session())).hasStatus(HttpStatus.OK);

		List<String> secrets = List.of(GithubOAuthStub.ACCESS_TOKEN, GithubOAuthStub.REFRESH_TOKEN);
		for (String secret : secrets) {
			assertThat(rowsContainingInAppTables(secret)).as("앱 테이블에 토큰이 있다: " + secret).isZero();
			assertThat(sessionAttributesContaining(secret)).as("세션 속성에 토큰이 있다: " + secret).isZero();
			assertThat(output.getAll()).as("로그에 토큰이 있다").doesNotContain(secret);
		}
		// 검사가 헛돌지 않는지: 세션 속성에는 로그인 정보(보안 컨텍스트)가 실제로 저장되어 있다.
		assertThat(sessionAttributesContaining("SPRING_SECURITY_CONTEXT")).isPositive();
		String principalName = String.valueOf(userIdOf(4601L));
		assertThat((Object) authorizedClientService.loadAuthorizedClient("github", principalName))
			.as("인가된 클라이언트(토큰)를 메모리에도 두지 않는다")
			.isNull();
	}

	@Test
	@DisplayName("S04: 세션에는 GitHub 프로필 전체가 아니라 최소한의 로그인 정보만 저장한다 (이메일 없음)")
	void sessionDoesNotStoreGithubProfile() {
		login(4701L, "octo-profile", "https://avatars.example.com/u/4701");

		assertThat(sessionAttributesContaining(GithubOAuthStub.PROFILE_EMAIL)).isZero();
		assertThat(rowsContainingInAppTables(GithubOAuthStub.PROFILE_EMAIL)).isZero();
	}

	/**
	 * Spring Security는 로그인 결과에 요청 정보(접속 IP, 로그인 전 세션 ID)를 붙여 세션에 함께 저장한다. 세션은 30일 롤링이라
	 * 끝없이 이어질 수 있어서, 접속 기록을 짧게 두는 9.8과 맞지 않는다.
	 */
	@Test
	@DisplayName("S04: 세션에는 로그인한 브라우저의 접속 IP를 저장하지 않는다")
	void sessionDoesNotStoreClientIp() {
		login(4801L, "octo-ip", "https://avatars.example.com/u/4801");

		assertThat(sessionAttributesContaining(CLIENT_IP)).isZero();
		assertThat(rowsContainingInAppTables(CLIENT_IP)).isZero();
	}

	private Map<String, Object> userRow(long githubId) {
		return jdbc.sql("select github_login, avatar_url, created_at, deleted_at from studybot.users where github_id = :id")
			.param("id", githubId)
			.query()
			.singleRow();
	}

	private long countUsers() {
		return jdbc.sql("select count(*) from studybot.users").query(Long.class).single();
	}

	/** studybot 스키마의 모든 테이블에서 행 전체를 글자로 바꿔 찾는다. bytea 컬럼은 따로 본다. */
	private long rowsContainingInAppTables(String text) {
		List<String> tables = jdbc
			.sql("select table_name from information_schema.tables where table_schema = 'studybot' "
					+ "and table_type = 'BASE TABLE'")
			.query(String.class)
			.list();
		long count = 0;
		for (String table : tables) {
			count += jdbc.sql("select count(*) from studybot.\"" + table + "\" t where t::text like :pattern")
				.param("pattern", "%" + text + "%")
				.query(Long.class)
				.single();
		}
		return count;
	}

	/** 세션 속성(직렬화된 bytea) 안에 이 글자의 바이트가 들어 있는 행 수. 이름도 함께 본다. */
	private long sessionAttributesContaining(String text) {
		return jdbc
			.sql("select count(*) from studybot.spring_session_attributes "
					+ "where position(convert_to(:text, 'UTF8') in attribute_bytes) > 0 or attribute_name = :text")
			.param("text", text)
			.query(Long.class)
			.single();
	}

	private static Instant toInstant(Object value) {
		if (value instanceof java.sql.Timestamp timestamp) {
			return timestamp.toInstant();
		}
		return ((OffsetDateTime) value).toInstant();
	}

}
