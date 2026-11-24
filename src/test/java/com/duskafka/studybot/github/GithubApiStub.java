package com.duskafka.studybot.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * GitHub REST API를 흉내 낸다 (S07, external-systems.md '개발 중에 외부 시스템 다루기'). 테스트는 실제 GitHub에 접속하지 않는다.
 *
 * <ul>
 * <li>설치 토큰 발급 {@code POST /app/installations/{id}/access_tokens} → 201 {@code {token, expires_at}}</li>
 * <li>설치 레포 목록 {@code GET /installation/repositories} → 200 {@code {total_count, repositories}}. 설치 토큰마다 다른 목록을
 * 돌려줘서, 남의 설치 토큰으로는 내 레포가 보이지 않는다.</li>
 * </ul>
 *
 * <p>서버는 JVM에 하나만 띄우고 끄지 않는다 ({@code GithubOAuthStub}과 같은 방식). 앱은
 * {@code studybot.github.app.api-base-url}로 이 주소를 받는다 ({@code GithubAppTestConfiguration}).
 * 토큰은 로그·DB에 남지 않았는지 찾기 쉽게 눈에 띄는 가짜 문자열이다.
 */
public final class GithubApiStub {

	public static final String API_VERSION = "2026-03-10";

	private static final String TOKEN_PREFIX = "ghs_S07FakeInstallationToken_";

	private static final AtomicReference<Runnable> BEFORE_REPOSITORIES_RESPONSE = new AtomicReference<>();

	private static final WireMockServer SERVER = new WireMockServer(
			wireMockConfig().dynamicPort().extensions(new RepositoriesResponseHook()));

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private GithubApiStub() {
	}

	public static synchronized WireMockServer server() {
		if (!SERVER.isRunning()) {
			SERVER.start();
		}
		return SERVER;
	}

	public static String baseUrl() {
		return server().baseUrl();
	}

	public static void reset() {
		server().resetAll();
		BEFORE_REPOSITORIES_RESPONSE.set(null);
	}

	/**
	 * 다음 레포 목록 응답을 보내기 직전에 한 번 실행한다. 앱이 GitHub 응답을 기다리는 사이에 일어나는 일(웹훅이 설치를 바꿈)을
	 * 시간에 기대지 않고 재현할 때 쓴다. WireMock 요청 스레드에서 돌아서 앱의 트랜잭션과 다른 DB 연결을 쓴다.
	 */
	public static void beforeNextRepositoriesResponse(Runnable action) {
		BEFORE_REPOSITORIES_RESPONSE.set(action);
	}

	/** 이 설치에 발급하는 가짜 토큰. 같은 설치에 다시 발급하면 끝 번호가 바뀐다. */
	public static String tokenFor(long installationId, int issue) {
		return TOKEN_PREFIX + installationId + "_" + issue;
	}

	/** 설치 토큰 발급이 성공한다. 발급마다 {@link #tokenFor(long, int)}의 번호가 1부터 올라간다. */
	public static void stubInstallationToken(long installationId, Instant expiresAt) {
		stubInstallationTokens(installationId, List.of(expiresAt));
	}

	/** 발급할 때마다 차례로 다른 토큰과 만료 시각을 준다. 마지막 것은 그 뒤로도 계속 준다. */
	public static void stubInstallationTokens(long installationId, List<Instant> expiresAts) {
		String scenario = "token-" + installationId;
		for (int i = 0; i < expiresAts.size(); i++) {
			int issue = i + 1;
			ScenarioMappingBuilder mapping = post(urlPathEqualTo(tokenPath(installationId))).inScenario(scenario)
				.whenScenarioStateIs(i == 0 ? Scenario.STARTED : "issued-" + i);
			if (i < expiresAts.size() - 1) {
				mapping = mapping.willSetStateTo("issued-" + issue);
			}
			server().stubFor(mapping.willReturn(aResponse().withStatus(201)
				.withHeader("Content-Type", "application/json; charset=utf-8")
				.withBody(json(Map.of("token", tokenFor(installationId, issue), "expires_at", expiresAts.get(i).toString(),
						"permissions", Map.of("contents", "read", "metadata", "read"), "repository_selection",
						"selected")))));
		}
	}

	/** 설치 토큰 발급이 이 상태 코드로 실패한다 (예: 설치가 정지됐거나 GitHub 장애). */
	public static void stubInstallationTokenFailure(long installationId, int status) {
		server().stubFor(post(urlPathEqualTo(tokenPath(installationId))).willReturn(aResponse().withStatus(status)
			.withHeader("Content-Type", "application/json; charset=utf-8")
			.withBody("{\"message\": \"S07 test failure\", \"documentation_url\": \"https://docs.github.com/rest\"}")));
	}

	/**
	 * 이 설치 토큰으로 부른 레포 목록. 요청의 page·per_page와 상관없이 {@code repositories}를 한 페이지로 준다.
	 * 페이지를 나눠 주려면 {@link #stubRepositoriesPage}를 쓴다.
	 */
	public static void stubRepositories(String token, Repo... repositories) {
		server().stubFor(get(urlPathEqualTo("/installation/repositories"))
			.withHeader("Authorization", equalTo("Bearer " + token))
			.willReturn(repositoriesResponse(repositories.length, List.of(repositories))));
	}

	/** 이 설치 토큰으로 부른 레포 목록의 한 페이지 (GitHub의 page는 1부터). */
	public static void stubRepositoriesPage(String token, int page, int totalCount, Repo... repositories) {
		server().stubFor(get(urlPathEqualTo("/installation/repositories"))
			.withHeader("Authorization", equalTo("Bearer " + token))
			.withQueryParam("page", equalTo(Integer.toString(page)))
			.willReturn(repositoriesResponse(totalCount, List.of(repositories))));
	}

	/** 이 설치 토큰으로 부른 레포 목록이 {@code total_count} 없이 온다 (GitHub 스키마에서는 필수인 값이 빠진 망가진 응답). */
	public static void stubRepositoriesWithoutTotalCount(String token, Repo... repositories) {
		server().stubFor(get(urlPathEqualTo("/installation/repositories"))
			.withHeader("Authorization", equalTo("Bearer " + token))
			.willReturn(aResponse().withStatus(200)
				.withHeader("Content-Type", "application/json; charset=utf-8")
				.withBody(json(Map.of("repository_selection", "selected", "repositories", items(List.of(repositories)))))));
	}

	/** 레포 목록 조회가 이 상태 코드로 실패한다. */
	public static void stubRepositoriesFailure(String token, int status) {
		server().stubFor(get(urlPathEqualTo("/installation/repositories"))
			.withHeader("Authorization", equalTo("Bearer " + token))
			.willReturn(aResponse().withStatus(status)
				.withHeader("Content-Type", "application/json; charset=utf-8")
				.withBody("{\"message\": \"S07 test failure\"}")));
	}

	public static List<LoggedRequest> tokenRequests(long installationId) {
		return server().findAll(postRequestedFor(urlPathEqualTo(tokenPath(installationId))));
	}

	public static List<LoggedRequest> repositoryRequests() {
		return server().findAll(getRequestedFor(urlPathEqualTo("/installation/repositories")));
	}

	private static String tokenPath(long installationId) {
		return "/app/installations/" + installationId + "/access_tokens";
	}

	private static ResponseDefinitionBuilder repositoriesResponse(int totalCount,
			List<Repo> repositories) {
		return aResponse().withStatus(200)
			.withHeader("Content-Type", "application/json; charset=utf-8")
			.withBody(json(Map.of("total_count", totalCount, "repository_selection", "selected", "repositories",
					items(repositories))));
	}

	private static List<Map<String, Object>> items(List<Repo> repositories) {
		List<Map<String, Object>> items = new ArrayList<>();
		for (Repo repo : repositories) {
			String[] ownerAndName = repo.fullName().split("/", 2);
			items.add(Map.of("id", repo.id(), "node_id", "FAKE_NODE_ID", "name", ownerAndName[1], "full_name",
					repo.fullName(), "private", repo.isPrivate(), "default_branch", repo.defaultBranch(), "owner",
					Map.of("login", ownerAndName[0], "id", 100000001L, "type", "User")));
		}
		return items;
	}

	private static String json(Object value) {
		return JSON.writeValueAsString(value);
	}

	/** 레포 목록 응답 직전에 {@link #beforeNextRepositoriesResponse}로 맡긴 일을 한 번 실행한다. */
	static final class RepositoriesResponseHook implements ServeEventListener {

		@Override
		public String getName() {
			return "studybot-repositories-response-hook";
		}

		@Override
		public void beforeResponseSent(ServeEvent serveEvent, Parameters parameters) {
			if (serveEvent.getRequest().getUrl().startsWith("/installation/repositories")) {
				Runnable action = BEFORE_REPOSITORIES_RESPONSE.getAndSet(null);
				if (action != null) {
					action.run();
				}
			}
		}

	}

	/** 목록에 넣을 레포. {@code fullName}은 {@code owner/name}. */
	public record Repo(long id, String fullName, String defaultBranch, boolean isPrivate) {

		public static Repo of(long id, String fullName) {
			return new Repo(id, fullName, "main", false);
		}

	}

}
