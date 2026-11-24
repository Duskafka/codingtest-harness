package com.duskafka.studybot.github.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * GitHub REST API 클라이언트 (S07 만들 것 4). 지금 쓰는 엔드포인트는 둘이다.
 *
 * <ul>
 * <li>설치 토큰 발급 {@code POST /app/installations/{id}/access_tokens} (App JWT로 인증, 201)
 * <a href="https://docs.github.com/en/rest/apps/apps#create-an-installation-access-token-for-an-app">GitHub 문서</a></li>
 * <li>설치 레포 목록 {@code GET /installation/repositories} (설치 토큰으로 인증, {@code per_page} 최대 100)
 * <a href="https://docs.github.com/en/rest/apps/installations#list-repositories-accessible-to-the-app-installation">GitHub 문서</a></li>
 * </ul>
 *
 * 연결 3초·응답 10초 시간 제한을 둔다 (architecture.md 6장 ⑥). 실패는 {@link GithubApiUnavailableException}(503)으로 바꾼다.
 * 로그에는 설치 ID와 상태 코드만 남긴다. 토큰·JWT·응답 본문은 남기지 않는다.
 */
@Slf4j
public class GithubApiClient {

	/** GitHub가 권하는 API 버전 헤더 값 (문서 예시, 2026-11 확인). */
	static final String API_VERSION = "2026-03-10";

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

	static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	/** GitHub {@code per_page} 최대값. */
	public static final int MAX_PER_PAGE = 100;

	private final RestClient rest;

	private final GithubAppJwt jwt;

	GithubApiClient(RestClient rest, GithubAppJwt jwt) {
		this.rest = rest;
		this.jwt = jwt;
	}

	public static GithubApiClient create(URI baseUrl, GithubAppJwt jwt) {
		HttpClient http = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.proxy(ProxySelector.getDefault())
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		RestClient rest = RestClient.builder()
			.baseUrl(baseUrl.toString())
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
			.defaultHeader("X-GitHub-Api-Version", API_VERSION)
			.defaultHeader(HttpHeaders.USER_AGENT, "studybot")
			.build();
		return new GithubApiClient(rest, jwt);
	}

	/** 설치 토큰을 새로 발급받는다. 캐시는 {@link InstallationTokenCache}가 한다. */
	public InstallationToken createInstallationToken(long installationId) {
		TokenResponse body;
		try {
			body = this.rest.post()
				.uri("/app/installations/{installationId}/access_tokens", installationId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.jwt.create())
				.retrieve()
				.body(TokenResponse.class);
		}
		catch (RestClientException ex) {
			throw unavailable("설치 토큰 발급", installationId, ex);
		}
		if (body == null || body.token() == null || body.token().isBlank() || body.expiresAt() == null) {
			log.warn("GitHub 설치 토큰 응답에 값이 없어요: installation={}", installationId);
			throw new GithubApiUnavailableException();
		}
		try {
			return new InstallationToken(body.token(), Instant.parse(body.expiresAt()));
		}
		catch (DateTimeParseException ex) {
			log.warn("GitHub 설치 토큰의 만료 시각을 읽지 못했어요: installation={}", installationId);
			throw new GithubApiUnavailableException();
		}
	}

	/**
	 * 설치 토큰으로 설치 레포 목록의 한 페이지를 읽는다.
	 *
	 * @param page GitHub의 페이지 번호 (1부터)
	 * @param perPage 한 페이지 크기 (1~{@value #MAX_PER_PAGE})
	 */
	public RepositoryPage listRepositories(long installationId, String installationToken, int page, int perPage) {
		RepositoriesResponse body;
		try {
			body = this.rest.get()
				.uri(uri -> uri.path("/installation/repositories")
					.queryParam("per_page", perPage)
					.queryParam("page", page)
					.build())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
				.retrieve()
				.body(RepositoriesResponse.class);
		}
		catch (RestClientException ex) {
			throw unavailable("설치 레포 목록", installationId, ex);
		}
		if (body == null) {
			log.warn("GitHub 설치 레포 목록 응답이 비었어요: installation={}", installationId);
			throw new GithubApiUnavailableException();
		}
		if (body.totalCount() == null) {
			// GitHub 스키마에서 total_count는 필수다. 없으면 목록이 완전한지 알 수 없어서, 페이지 크기로 대신 채우지 않는다
			// (빠진 연결 끊기와 자동 연결이 이 값으로 판단한다, PR #14 2차 리뷰 반영).
			log.warn("GitHub 설치 레포 목록 응답에 total_count가 없어요: installation={}", installationId);
			throw new GithubApiUnavailableException();
		}
		List<InstalledRepository> repositories = body.repositories() == null ? List.of()
				: body.repositories()
					.stream()
					.filter(repo -> repo.id() != null && repo.fullName() != null && repo.defaultBranch() != null)
					.map(repo -> new InstalledRepository(repo.id(), repo.fullName(), repo.defaultBranch(),
							Boolean.TRUE.equals(repo.isPrivate())))
					.toList();
		return new RepositoryPage(body.totalCount(), repositories);
	}

	private static GithubApiUnavailableException unavailable(String operation, long installationId,
			RestClientException ex) {
		// 예외 메시지에는 응답 본문이 들어 있을 수 있어 로그에 싣지 않는다. 상태 코드나 예외 종류만 남긴다.
		if (ex instanceof RestClientResponseException response) {
			log.warn("GitHub API 호출이 실패했어요: {}, installation={}, status={}", operation, installationId,
					response.getStatusCode().value());
		}
		else {
			log.warn("GitHub API에 닿지 못했어요: {}, installation={}, error={}", operation, installationId,
					Objects.requireNonNullElse(ex.getCause(), ex).getClass().getName());
		}
		return new GithubApiUnavailableException();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(@Nullable String token, @JsonProperty("expires_at") @Nullable String expiresAt) {

		@Override
		public String toString() {
			return "TokenResponse[token=***, expiresAt=" + this.expiresAt + "]";
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RepositoriesResponse(@JsonProperty("total_count") @Nullable Integer totalCount,
			@Nullable List<RepositoryJson> repositories) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RepositoryJson(@Nullable Long id, @JsonProperty("full_name") @Nullable String fullName,
			@JsonProperty("default_branch") @Nullable String defaultBranch,
			@JsonProperty("private") @Nullable Boolean isPrivate) {
	}

}
