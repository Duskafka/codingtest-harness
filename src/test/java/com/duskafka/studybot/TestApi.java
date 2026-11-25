package com.duskafka.studybot;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.duskafka.studybot.auth.LoginUser;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
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
 * 로그인한 사용자로 API를 부르는 테스트 도우미 (S10). 사용자 ID만 알면 된다. GitHub ID는 DB에서 읽는다.
 *
 * <ul>
 * <li>로그인은 세션에 담기는 인증 주체({@link LoginUser})를 요청에 붙여 흉내 낸다 (S05·S07 테스트와 같은 방식).</li>
 * <li>CSRF 토큰은 SPA처럼 XSRF-TOKEN 쿠키로 받아 X-XSRF-TOKEN 헤더로 돌려보낸다. Spring Security 테스트의 {@code csrf()}
 * 후처리기는 공유 컨텍스트의 CSRF 토큰 저장소를 바꿔 끼워 뒤 테스트를 망가뜨린다 (S04 인계 기록).</li>
 * </ul>
 */
public final class TestApi {

	private static final String XSRF_COOKIE = "XSRF-TOKEN";

	private static final String XSRF_HEADER = "X-XSRF-TOKEN";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final MockMvcTester mvc;

	private final JdbcClient jdbc;

	private @Nullable String csrfToken;

	public TestApi(MockMvcTester mvc, JdbcClient jdbc) {
		this.mvc = mvc;
		this.jdbc = jdbc;
	}

	/** 이 사용자로 로그인한 GET. */
	public MvcTestResult get(long userId, String uri, Object... uriVariables) {
		return this.mvc.get().uri(uri, uriVariables).with(as(userId)).exchange();
	}

	/** 이 사용자로 로그인한 GET. 쿼리 값은 인코딩하지 않고 요청 파라미터로 붙인다 (이름, 값, 이름, 값, ...). */
	public MvcTestResult getWithParams(long userId, String uri, String... namesAndValues) {
		MockMvcRequestBuilder request = this.mvc.get().uri(uri).with(as(userId));
		for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
			request = request.param(namesAndValues[i], namesAndValues[i + 1]);
		}
		return request.exchange();
	}

	/** 로그인하지 않은 GET. */
	public MvcTestResult getAnonymous(String uri, Object... uriVariables) {
		return this.mvc.get().uri(uri, uriVariables).exchange();
	}

	/** 이 사용자로 로그인한 JSON PATCH (CSRF 포함). */
	public MvcTestResult patchJson(long userId, String uri, String body, Object... uriVariables) {
		return withCsrf(this.mvc.patch().uri(uri, uriVariables)).with(as(userId))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	/** 로그인하지 않은 JSON PATCH (CSRF 포함). */
	public MvcTestResult patchJsonAnonymous(String uri, String body, Object... uriVariables) {
		return withCsrf(this.mvc.patch().uri(uri, uriVariables)).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	public static JsonNode json(MvcTestResult result) {
		try {
			return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private RequestPostProcessor as(long userId) {
		long githubId = this.jdbc.sql("select github_id from studybot.users where id = :id")
			.param("id", userId)
			.query(Long.class)
			.single();
		LoginUser principal = new LoginUser(userId, githubId);
		return authentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "github"));
	}

	private MockMvcRequestBuilder withCsrf(MockMvcRequestBuilder request) {
		String token = csrfToken();
		return request.cookie(new Cookie(XSRF_COOKIE, token)).header(XSRF_HEADER, token);
	}

	private String csrfToken() {
		if (this.csrfToken == null) {
			MvcTestResult result = this.mvc.get().uri("/actuator/health").exchange();
			this.csrfToken = result.getResponse()
				.getHeaders("Set-Cookie")
				.stream()
				.filter(header -> header.startsWith(XSRF_COOKIE + "="))
				.map(header -> header.substring(XSRF_COOKIE.length() + 1).split(";", 2)[0])
				.filter(value -> !value.isEmpty())
				.findFirst()
				.orElseThrow(() -> new AssertionError("XSRF-TOKEN 쿠키가 없다"));
		}
		return this.csrfToken;
	}

}
