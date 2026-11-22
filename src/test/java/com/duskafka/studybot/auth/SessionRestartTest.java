package com.duskafka.studybot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.StudybotApplication;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 애플리케이션을 다시 띄워도 로그인 세션이 유지된다 (S04 수용 기준, D22, 결정 제안서 002).
 *
 * <p>통합 테스트 컨텍스트는 DB 컨테이너와 수명이 묶여 있어서, 이 테스트는 DB 컨테이너 하나를 직접 띄우고
 * 그 위에서 앱을 두 번 띄운다(내장 Tomcat, 임의 포트). 요청은 실제 HTTP로 보내고 쿠키는 직접 넘긴다.
 * 배포할 때 컨테이너를 바꿔도 로그아웃되지 않아야 한다는 요구(infra.md 9.2)와 같은 상황이다.
 */
class SessionRestartTest {

	private static final String SESSION_COOKIE = LoginTestSupport.SESSION_COOKIE;

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	@BeforeAll
	static void startDatabase() {
		POSTGRES.start();
	}

	@AfterAll
	static void stopDatabase() {
		POSTGRES.stop();
	}

	@Test
	@DisplayName("S04: 애플리케이션을 다시 띄워도 세션이 유지된다 (세션이 DB에 있음, D22)")
	void sessionSurvivesApplicationRestart() throws Exception {
		GithubOAuthStub.stubSuccessfulLogin(8101L, "restart-user", "https://avatars.example.com/u/8101");
		String session;
		String meBeforeRestart;
		try (ConfigurableApplicationContext app = startApplication()) {
			String base = baseUrl(app);
			session = login(base);
			HttpResponse<String> me = get(base + "/api/me", session);
			assertThat(me.statusCode()).isEqualTo(200);
			meBeforeRestart = me.body();
		}

		try (ConfigurableApplicationContext restarted = startApplication()) {
			HttpResponse<String> me = get(baseUrl(restarted) + "/api/me", session);

			assertThat(me.statusCode()).as("다시 띄운 앱이 같은 세션 쿠키를 알아본다").isEqualTo(200);
			assertThat(me.body()).isEqualTo(meBeforeRestart).contains("restart-user");
		}
	}

	private static ConfigurableApplicationContext startApplication() {
		Map<String, Object> properties = new HashMap<>();
		properties.put("server.port", "0");
		properties.put("spring.datasource.url", POSTGRES.getJdbcUrl());
		properties.put("spring.datasource.username", POSTGRES.getUsername());
		properties.put("spring.datasource.password", POSTGRES.getPassword());
		String provider = "spring.security.oauth2.client.provider.github.";
		properties.put(provider + "authorization-uri", GithubOAuthStub.baseUrl() + GithubOAuthStub.AUTHORIZE_PATH);
		properties.put(provider + "token-uri", GithubOAuthStub.baseUrl() + GithubOAuthStub.TOKEN_PATH);
		properties.put(provider + "user-info-uri", GithubOAuthStub.baseUrl() + GithubOAuthStub.USER_PATH);
		return new SpringApplicationBuilder(StudybotApplication.class).properties(properties).run();
	}

	private static String baseUrl(ConfigurableApplicationContext app) {
		return "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
	}

	/** 인가 요청 → GitHub 콜백을 거쳐 로그인하고, 로그인 뒤 세션 쿠키 값을 돌려준다. */
	private String login(String base) throws IOException, InterruptedException {
		HttpResponse<String> authorization = get(base + LoginTestSupport.AUTHORIZATION_PATH, null);
		assertThat(authorization.statusCode()).isEqualTo(302);
		String preLogin = sessionCookie(authorization).orElseThrow();
		String state = LoginTestSupport.queryParams(authorization.headers().firstValue("Location").orElseThrow())
			.get("state");

		HttpResponse<String> callback = get(base + LoginTestSupport.CALLBACK_PATH + "?code=s04-test-code&state=" + state,
				preLogin);
		assertThat(callback.statusCode()).isEqualTo(302);
		assertThat(callback.headers().firstValue("Location")).hasValueSatisfying(
				location -> assertThat(location).endsWith("/"));
		return sessionCookie(callback).orElseThrow();
	}

	private HttpResponse<String> get(String url, String session) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
		if (session != null) {
			request.header("Cookie", SESSION_COOKIE + "=" + session);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static Optional<String> sessionCookie(HttpResponse<?> response) {
		return response.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(header -> header.startsWith(SESSION_COOKIE + "=") && !header.contains("Max-Age=0"))
			.reduce((first, second) -> second)
			.map(header -> header.substring(SESSION_COOKIE.length() + 1).split(";", 2)[0]);
	}

}
