package com.duskafka.studybot.github.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.duskafka.studybot.github.GithubAppTestKeys;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateCrtKey;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * S07 만들 것 4: App private key는 서버 파일 경로({@code GITHUB_APP_PRIVATE_KEY_PATH})로만 받는다 (infra.md 9.6,
 * 사람이 정함 2026-11-24 "파일만"). 없거나 읽지 못하면 앱이 뜨지 않고, 오류 메시지에 키 내용을 싣지 않는다.
 */
class GithubApiConfigurationTest {

	@TempDir
	Path dir;

	@Test
	@DisplayName("S07: private key를 파일 경로에서 읽는다 (GitHub가 주는 PKCS#1 그대로)")
	void readsKeyFromFile() throws IOException {
		Path file = Files.writeString(this.dir.resolve("app.pem"), GithubAppTestKeys.pkcs1Pem(), StandardCharsets.US_ASCII);

		RSAPrivateCrtKey key = (RSAPrivateCrtKey) GithubApiConfiguration.loadPrivateKey(properties(file.toString()));

		assertThat(key.getModulus()).isEqualTo(GithubAppTestKeys.publicKey().getModulus());
	}

	@Test
	@DisplayName("S07: private key 파일 경로가 없으면 앱이 뜨지 않고 GITHUB_APP_PRIVATE_KEY_PATH를 넣으라고 알린다")
	void missingPathStopsStartup() {
		assertThatThrownBy(() -> GithubApiConfiguration.loadPrivateKey(properties(null)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("GITHUB_APP_PRIVATE_KEY_PATH");
		assertThatThrownBy(() -> GithubApiConfiguration.loadPrivateKey(properties("  ")))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("S07: 파일이 없거나 키를 읽지 못하면 앱이 뜨지 않고, 오류에 키 내용을 싣지 않는다")
	void unreadableKeyStopsStartupWithoutLeakingKey() throws IOException {
		String body = GithubAppTestKeys.pkcs1Base64();
		Path broken = Files.writeString(this.dir.resolve("broken.pem"),
				"-----BEGIN RSA PRIVATE KEY-----\n" + body.substring(0, 200) + "\n-----END RSA PRIVATE KEY-----\n",
				StandardCharsets.US_ASCII);

		assertThatThrownBy(() -> GithubApiConfiguration.loadPrivateKey(properties(this.dir.resolve("none.pem").toString())))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> GithubApiConfiguration.loadPrivateKey(properties(broken.toString())))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("broken.pem")
			.satisfies(ex -> assertThat(String.valueOf(ex.getMessage()) + ex.getCause()).doesNotContain(body.substring(0, 40)));
	}

	@Test
	@DisplayName("S07: 경로 자리에 키 내용(PEM)을 넣으면 그 값을 되풀이하지 않고 거절한다 (기동 실패 로그에 키가 남지 않게)")
	void keyContentInPathIsRejectedWithoutEchoingIt() {
		String body = GithubAppTestKeys.pkcs1Base64();
		GithubAppProperties misconfigured = properties(GithubAppTestKeys.pkcs1Pem());

		assertThatThrownBy(() -> GithubApiConfiguration.loadPrivateKey(misconfigured))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("GITHUB_APP_PRIVATE_KEY_PATH")
			.satisfies(ex -> assertThat(chain(ex)).doesNotContain(body.substring(0, 40)));
		assertThat(misconfigured.toString()).doesNotContain(body.substring(0, 40));
	}

	private static String chain(Throwable ex) {
		StringBuilder text = new StringBuilder();
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			text.append(cause).append('\n');
		}
		return text.toString();
	}

	private static GithubAppProperties properties(@Nullable String privateKeyPath) {
		return new GithubAppProperties("Iv1.test-client-id", "studybot-test", privateKeyPath, URI.create("http://localhost"),
				URI.create("http://localhost"));
	}

}
