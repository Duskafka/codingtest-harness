package com.duskafka.studybot.github;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * S07 엔드포인트 통합 테스트의 공통 도우미.
 *
 * <ul>
 * <li>사용자는 DB에 바로 넣고, 로그인은 세션에 담기는 인증 주체({@link LoginUser})를 요청에 붙여 흉내 낸다 (S05 테스트와 같음).</li>
 * <li>설치 state의 1회용 값은 세션에 있다. 세션은 Spring Session이 쿠키로 찾으므로, 응답의 세션 쿠키를 다음 요청에 다시 붙인다
 * ({@link Browser}).</li>
 * <li>GitHub API는 {@link GithubApiStub}(WireMock)이다. 테스트마다 비운다.</li>
 * <li>CSRF 토큰은 SPA처럼 XSRF-TOKEN 쿠키로 받아 X-XSRF-TOKEN 헤더로 돌려보낸다.</li>
 * </ul>
 */
@IntegrationTest
abstract class GithubConnectionTestSupport {

	static final String SESSION_COOKIE = "__Host-Http-SESSION";

	static final String XSRF_COOKIE = "XSRF-TOKEN";

	static final String XSRF_HEADER = "X-XSRF-TOKEN";

	static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected JdbcClient jdbc;

	@Autowired
	protected MutableClock clock;

	protected GithubConnectionRows rows;

	private @Nullable String csrfToken;

	@BeforeEach
	void setUpGithub() {
		rows = new GithubConnectionRows(jdbc);
		GithubApiStub.reset();
	}

	protected TestUser newUser(String login) {
		return rows.newUser(login, clock.instant());
	}

	/** 이 사용자로 로그인한 요청. */
	protected static RequestPostProcessor as(TestUser user) {
		LoginUser principal = new LoginUser(user.id(), user.githubId());
		return authentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "github"));
	}

	protected MvcTestResult get(TestUser user, String uri) {
		return mvc.get().uri(uri).with(as(user)).exchange();
	}

	protected MvcTestResult putJson(TestUser user, String uri, String body) {
		return withCsrf(mvc.put().uri(uri)).with(as(user))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	protected MvcTestResult linkRepository(TestUser user, long repoId) {
		return putJson(user, "/api/me/linked-repository", "{\"repoId\": " + repoId + "}");
	}

	protected MvcTestResult repositories(TestUser user) {
		return get(user, "/api/me/github/repositories");
	}

	protected MockMvcRequestBuilder withCsrf(MockMvcRequestBuilder request) {
		String token = csrfToken();
		return request.cookie(new Cookie(XSRF_COOKIE, token)).header(XSRF_HEADER, token);
	}

	/** 설치 토큰 발급이 1시간짜리로 성공하고, 그 토큰으로 이 레포들이 보이게 한다. 발급한 토큰을 돌려준다. */
	protected String stubInstallation(long installationId, GithubApiStub.Repo... repositories) {
		GithubApiStub.stubInstallationToken(installationId, clock.instant().plusSeconds(3600));
		String token = GithubApiStub.tokenFor(installationId, 1);
		GithubApiStub.stubRepositories(token, repositories);
		return token;
	}

	protected static JsonNode json(MvcTestResult result) {
		return JSON.readTree(body(result));
	}

	protected static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	protected static Optional<String> cookieValue(MvcTestResult result, String name) {
		String found = null;
		for (String header : result.getResponse().getHeaders("Set-Cookie")) {
			if (header.startsWith(name + "=")) {
				found = header.substring(name.length() + 1).split(";", 2)[0];
			}
		}
		return Optional.ofNullable(found).filter(value -> !value.isEmpty());
	}

	private String csrfToken() {
		if (csrfToken == null) {
			MvcTestResult result = mvc.get().uri("/actuator/health").exchange();
			csrfToken = cookieValue(result, XSRF_COOKIE).orElseThrow(() -> new AssertionError("XSRF-TOKEN 쿠키가 없다"));
		}
		return csrfToken;
	}

	/** 한 사용자의 브라우저. 세션 쿠키를 받아 두었다가 다음 요청에 붙인다. */
	protected final class Browser {

		private final TestUser user;

		private @Nullable String session;

		Browser(TestUser user) {
			this.user = user;
		}

		MvcTestResult get(String uri) {
			MockMvcRequestBuilder request = mvc.get().uri(uri).with(as(user));
			if (session != null) {
				request = request.cookie(new Cookie(SESSION_COOKIE, session));
			}
			MvcTestResult result = request.exchange();
			cookieValue(result, SESSION_COOKIE).ifPresent(value -> session = value);
			return result;
		}

		/** 설치 주소를 받아 그 안의 state를 돌려준다. */
		String installState() {
			MvcTestResult result = get("/api/github/install-url");
			String url = json(result).get("url").asString();
			return org.springframework.web.util.UriComponentsBuilder.fromUriString(url)
				.build()
				.getQueryParams()
				.getFirst("state");
		}

	}

}
