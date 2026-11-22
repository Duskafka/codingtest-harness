package com.duskafka.studybot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * S04 통합 테스트의 공통 설정과 도우미. GitHub 엔드포인트를 {@link GithubOAuthStub}로 바꾸고,
 * 실제 로그인 흐름(인가 요청 → GitHub 콜백)을 MockMvc로 거친다.
 *
 * <p>S04 테스트 클래스는 이 클래스를 상속해 Spring 컨텍스트(와 DB 컨테이너) 하나를 함께 쓴다.
 * 테스트끼리 DB를 공유하므로 사용자는 테스트마다 다른 github_id를 쓴다.
 */
@IntegrationTest
@Import(LoginTestSupport.TestEndpoints.class)
abstract class LoginTestSupport {

	static final String SESSION_COOKIE = "__Host-Http-SESSION";

	static final String XSRF_COOKIE = "XSRF-TOKEN";

	static final String XSRF_HEADER = "X-XSRF-TOKEN";

	static final String AUTHORIZATION_PATH = "/oauth2/authorization/github";

	static final String CALLBACK_PATH = "/login/oauth2/code/github";

	static final long THIRTY_DAYS_SECONDS = 30L * 24 * 60 * 60;

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected JdbcClient jdbc;

	@Autowired
	protected MutableClock clock;

	@DynamicPropertySource
	static void githubEndpoints(DynamicPropertyRegistry registry) {
		GithubOAuthStub.registerProperties(registry);
	}

	/** 인가 요청을 보낸다. 로그인 전 세션(인가 요청을 담음)이 만들어진다. */
	protected MvcTestResult startAuthorization() {
		MvcTestResult result = mvc.get().uri(AUTHORIZATION_PATH).exchange();
		assertThat(result).hasStatus(HttpStatus.FOUND);
		return result;
	}

	/** GitHub가 이 사용자로 응답하게 하고 로그인 흐름을 끝까지 거친다. */
	protected Login login(long githubId, String githubLogin, String avatarUrl) {
		GithubOAuthStub.stubSuccessfulLogin(githubId, githubLogin, avatarUrl);
		return completeLogin();
	}

	/** 미리 정해 둔 GitHub 응답으로 인가 요청과 콜백을 거친다. */
	protected Login completeLogin() {
		MvcTestResult authorization = startAuthorization();
		String location = authorization.getResponse().getHeader("Location");
		assertThat(location).as("인가 요청은 GitHub로 보낸다").isNotNull();
		String state = queryParams(location).get("state");
		String preLoginSession = cookieValue(authorization, SESSION_COOKIE)
			.orElseThrow(() -> new AssertionError("인가 요청이 세션 쿠키를 만들지 않았다"));
		MvcTestResult callback = mvc.get()
			.uri(CALLBACK_PATH + "?code={code}&state={state}", "s04-test-code", state)
			.cookie(new Cookie(SESSION_COOKIE, preLoginSession))
			.exchange();
		String session = cookieValue(callback, SESSION_COOKIE).orElse(preLoginSession);
		return new Login(authorization, callback, preLoginSession, session,
				cookieValue(callback, XSRF_COOKIE).orElse(null));
	}

	/** 로그인한 세션 쿠키를 붙인 요청. */
	protected MockMvcTester.MockMvcRequestBuilder withSession(MockMvcTester.MockMvcRequestBuilder request,
			String session) {
		return request.cookie(new Cookie(SESSION_COOKIE, session));
	}

	/** 이 세션의 CSRF 토큰. 쿠키가 아직 없으면 GET 요청 하나로 받는다 (SPA가 하는 것과 같다). */
	protected String csrfTokenFor(Login login) {
		if (login.xsrfToken() != null) {
			return login.xsrfToken();
		}
		MvcTestResult result = withSession(mvc.get().uri("/api/me"), login.session()).exchange();
		return cookieValue(result, XSRF_COOKIE).orElseThrow(() -> new AssertionError("XSRF-TOKEN 쿠키가 없다"));
	}

	protected long userIdOf(long githubId) {
		return jdbc.sql("select id from studybot.users where github_id = :githubId")
			.param("githubId", githubId)
			.query(Long.class)
			.single();
	}

	/** 응답의 Set-Cookie 헤더 중 이 이름의 마지막 것 (같은 응답에 여러 번 쓰면 브라우저는 마지막 것을 따른다). */
	static Optional<String> setCookieHeader(MvcTestResult result, String name) {
		List<String> headers = result.getResponse().getHeaders("Set-Cookie");
		String found = null;
		for (String header : headers) {
			if (header.startsWith(name + "=")) {
				found = header;
			}
		}
		return Optional.ofNullable(found);
	}

	/** Set-Cookie의 값. 지우는 쿠키(Max-Age=0)나 빈 값이면 없다고 본다. */
	static Optional<String> cookieValue(MvcTestResult result, String name) {
		return setCookieHeader(result, name).filter(header -> !header.contains("Max-Age=0"))
			.map(header -> header.substring(name.length() + 1).split(";", 2)[0])
			.filter(value -> !value.isEmpty());
	}

	static Map<String, String> queryParams(String uri) {
		return UriComponentsBuilder.fromUri(URI.create(uri))
			.build()
			.getQueryParams()
			.toSingleValueMap()
			.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey,
					entry -> java.net.URLDecoder.decode(entry.getValue(), StandardCharsets.UTF_8)));
	}

	static List<String> attributesOf(String header) {
		return Arrays.stream(header.split(";")).map(String::trim).toList();
	}

	/**
	 * 로그인 흐름 결과.
	 * @param authorization 인가 요청 응답
	 * @param callback GitHub 콜백 응답
	 * @param preLoginSession 인가 요청이 만든(로그인 전) 세션 쿠키 값
	 * @param session 로그인 뒤 세션 쿠키 값
	 * @param xsrfToken 콜백 응답이 내려 준 CSRF 토큰. 없으면 null
	 */
	record Login(MvcTestResult authorization, MvcTestResult callback, String preLoginSession, String session,
			@Nullable String xsrfToken) {
	}

	/** S04 테스트에만 등록되는 엔드포인트. 테스트 클래스 안의 클래스라 컴포넌트 스캔에서 빠진다. */
	@RestController
	static class TestEndpoints {

		/** 로그인 사용자의 상태를 바꾸는 요청 자리. CSRF 검사를 통과해야 여기까지 온다. */
		@PostMapping("/api/test/csrf")
		Map<String, String> csrfProtected() {
			return Map.of("result", "reached");
		}

		/** 웹훅 수신 자리. 서명 검증은 S06에서 붙인다. CSRF·세션 없이 여기까지 와야 한다. */
		@PostMapping("/webhooks/test")
		ResponseEntity<Void> webhook() {
			return ResponseEntity.accepted().build();
		}

	}

}
