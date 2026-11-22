package com.duskafka.studybot.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * GitHub의 OAuth 엔드포인트(토큰 교환, {@code GET /user})를 흉내 낸다. 테스트는 실제 GitHub에 접속하지 않는다
 * (S04 '주의할 것', external-systems.md '개발 중에 외부 시스템 다루기').
 *
 * <p>서버는 JVM에 하나만 띄우고 끄지 않는다. 테스트 컨텍스트가 캐시되어 같은 주소를 계속 쓰기 때문이다.
 * 토큰 값은 DB·로그에 남지 않는지 찾기 쉽게 눈에 띄는 가짜 문자열이다 (S04 수용 기준).
 */
final class GithubOAuthStub {

	static final String ACCESS_TOKEN = "ghu_S04FakeAccessToken_mustNotPersist";

	static final String REFRESH_TOKEN = "ghr_S04FakeRefreshToken_mustNotPersist";

	/** {@code GET /user} 응답에 넣는 이메일. 서비스는 이메일을 받지 않으므로 세션·DB에 남으면 안 된다 (security.md 9.8). */
	static final String PROFILE_EMAIL = "s04-should-not-be-stored@example.com";

	static final String AUTHORIZE_PATH = "/login/oauth/authorize";

	static final String TOKEN_PATH = "/login/oauth/access_token";

	static final String USER_PATH = "/user";

	private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

	private GithubOAuthStub() {
	}

	static synchronized WireMockServer server() {
		if (!SERVER.isRunning()) {
			SERVER.start();
		}
		return SERVER;
	}

	static String baseUrl() {
		return server().baseUrl();
	}

	/** 앱의 GitHub 엔드포인트를 이 서버로 바꾼다. 등록 ID github의 나머지 값은 CommonOAuth2Provider.GITHUB 기본값이다. */
	static void registerProperties(DynamicPropertyRegistry registry) {
		String provider = "spring.security.oauth2.client.provider.github.";
		registry.add(provider + "authorization-uri", () -> baseUrl() + AUTHORIZE_PATH);
		registry.add(provider + "token-uri", () -> baseUrl() + TOKEN_PATH);
		registry.add(provider + "user-info-uri", () -> baseUrl() + USER_PATH);
	}

	/** 토큰 교환과 {@code GET /user}가 성공하게 한다. 응답 모양은 GitHub App의 사용자 토큰(만료 켬)과 같다. */
	static void stubSuccessfulLogin(long githubId, String login, String avatarUrl) {
		WireMockServer server = server();
		server.resetAll();
		server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(okJson("""
				{"access_token": "%s", "token_type": "bearer", "scope": "", "expires_in": 28800,
				 "refresh_token": "%s", "refresh_token_expires_in": 15897600}
				""".formatted(ACCESS_TOKEN, REFRESH_TOKEN))));
		server.stubFor(get(urlPathEqualTo(USER_PATH)).withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
			.willReturn(okJson("""
					{"id": %d, "login": "%s", "avatar_url": "%s", "name": "S04 Tester", "email": "%s",
					 "type": "User", "site_admin": false}
					""".formatted(githubId, login, avatarUrl, PROFILE_EMAIL))));
	}

	/** 토큰 교환은 성공하지만 {@code GET /user}가 실패한다 (GitHub 장애나 토큰 문제). */
	static void stubUserEndpointFailure() {
		WireMockServer server = server();
		server.resetAll();
		server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(okJson("""
				{"access_token": "%s", "token_type": "bearer", "scope": "", "expires_in": 28800}
				""".formatted(ACCESS_TOKEN))));
		server.stubFor(get(urlPathEqualTo(USER_PATH)).willReturn(aResponse().withStatus(401)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"message\": \"Bad credentials\"}")));
	}

}
