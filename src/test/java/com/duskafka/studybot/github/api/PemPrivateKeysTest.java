package com.duskafka.studybot.github.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.duskafka.studybot.github.GithubAppTestKeys;
import java.security.interfaces.RSAPrivateCrtKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S07 만들 것 4: App private key 읽기. GitHub는 PKCS#1 PEM을 내려준다
 * (<a href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/managing-private-keys-for-github-apps">GitHub 문서</a>).
 * 운영자가 {@code openssl pkcs8}로 바꾼 PKCS#8도 받는다.
 */
class PemPrivateKeysTest {

	@Test
	@DisplayName("S07: GitHub가 주는 PKCS#1 PEM과 PKCS#8 PEM을 같은 키로 읽는다")
	void readsPkcs1AndPkcs8() {
		RSAPrivateCrtKey pkcs1 = (RSAPrivateCrtKey) PemPrivateKeys.parse(GithubAppTestKeys.pkcs1Pem());
		RSAPrivateCrtKey pkcs8 = (RSAPrivateCrtKey) PemPrivateKeys.parse(GithubAppTestKeys.pkcs8Pem());

		assertThat(pkcs1.getModulus()).isEqualTo(GithubAppTestKeys.publicKey().getModulus());
		assertThat(pkcs1.getPrivateExponent()).isEqualTo(pkcs8.getPrivateExponent());
	}

	@Test
	@DisplayName("S07: 환경 변수에 줄바꿈을 \\n 글자로 넣은 PEM도 읽는다")
	void readsEscapedNewlines() {
		String escaped = GithubAppTestKeys.pkcs1Pem().replace("\n", "\\n");

		RSAPrivateCrtKey key = (RSAPrivateCrtKey) PemPrivateKeys.parse(escaped);

		assertThat(key.getModulus()).isEqualTo(GithubAppTestKeys.publicKey().getModulus());
	}

	@Test
	@DisplayName("S07: 읽지 못한 키는 예외 메시지에 키 내용을 싣지 않는다")
	void failureDoesNotLeakKeyMaterial() {
		String body = GithubAppTestKeys.pkcs1Base64();
		String broken = "-----BEGIN RSA PRIVATE KEY-----\n" + body.substring(0, 200) + "\n-----END RSA PRIVATE KEY-----\n";
		String unsupported = "-----BEGIN ENCRYPTED PRIVATE KEY-----\n" + body.substring(0, 200)
				+ "\n-----END ENCRYPTED PRIVATE KEY-----\n";

		assertThatThrownBy(() -> PemPrivateKeys.parse(broken)).isInstanceOf(IllegalArgumentException.class)
			.satisfies(ex -> assertThat(String.valueOf(ex.getMessage()) + ex.getCause())
				.doesNotContain(body.substring(0, 40)));
		assertThatThrownBy(() -> PemPrivateKeys.parse(unsupported)).isInstanceOf(IllegalArgumentException.class)
			.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(body.substring(0, 40)));
		assertThatThrownBy(() -> PemPrivateKeys.parse("  ")).isInstanceOf(IllegalArgumentException.class);
	}

}
