package com.duskafka.studybot.github.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GitHub API 클라이언트 빈 (S07 만들 것 4). private key는 기동할 때 한 번 읽고, 없거나 읽지 못하면 앱이 뜨지 않는다.
 * 키 내용은 메모리의 {@link PrivateKey}로만 두고 로그·오류 메시지에 싣지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GithubAppProperties.class)
class GithubApiConfiguration {

	@Bean
	GithubAppJwt githubAppJwt(GithubAppProperties properties, Clock clock) {
		return new GithubAppJwt(properties.clientId(), loadPrivateKey(properties), clock);
	}

	@Bean
	GithubApiClient githubApiClient(GithubAppProperties properties, GithubAppJwt jwt) {
		return GithubApiClient.create(properties.apiBaseUrl(), jwt);
	}

	@Bean
	InstallationTokenCache installationTokenCache(GithubApiClient client, Clock clock) {
		return new InstallationTokenCache(client, clock);
	}

	/**
	 * private key를 서버 파일에서 읽는다 (infra.md 9.6, 사람이 정함 2026-11-24 "파일만"). 경로가 없거나, 파일을 읽지 못하거나,
	 * 키를 읽지 못하면 앱이 뜨지 않는다. 오류에는 경로만 싣고 키 내용은 싣지 않는다.
	 */
	static PrivateKey loadPrivateKey(GithubAppProperties properties) {
		if (!GithubAppProperties.hasText(properties.privateKeyPath())) {
			throw new IllegalStateException(
					"GitHub App private key 파일 경로가 없어요. GITHUB_APP_PRIVATE_KEY_PATH에 PEM 파일 경로를 넣어 주세요");
		}
		if (!properties.hasPlausibleKeyPath()) {
			// 경로 자리에 키 내용을 넣은 경우다. 값을 메시지·원인 예외에 되풀이하지 않는다 (기동 실패 로그에 키가 남지 않게).
			throw new IllegalStateException(
					"GITHUB_APP_PRIVATE_KEY_PATH에는 키 내용이 아니라 PEM 파일 경로를 넣어 주세요");
		}
		Path path = Path.of(properties.privateKeyPath());
		String pem;
		try {
			pem = Files.readString(path, StandardCharsets.US_ASCII);
		}
		catch (IOException ex) {
			throw new IllegalStateException("GitHub App private key 파일을 읽지 못했어요: " + path, ex);
		}
		try {
			return PemPrivateKeys.parse(pem);
		}
		catch (IllegalArgumentException ex) {
			// PemPrivateKeys의 메시지는 고정 문구라 키 내용이 없다. 어느 파일인지 알 수 있게 경로를 더한다.
			throw new IllegalStateException(ex.getMessage() + ": " + path, ex);
		}
	}

}
