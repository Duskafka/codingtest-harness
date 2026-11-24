package com.duskafka.studybot.github.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.github.GithubAppTestKeys;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * S07 만들 것 4: App JWT. RS256으로 서명하고 {@code iat}는 60초 전, {@code exp}는 10분 안, {@code iss}는 client ID다
 * (<a href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app">GitHub 문서</a>).
 */
class GithubAppJwtTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

	@Test
	@DisplayName("S07: App JWT는 RS256 서명이고 iat는 60초 전, exp는 9분 뒤(최대 10분 안), iss는 client ID다")
	void jwtClaimsAndSignature() throws Exception {
		MutableClock clock = new MutableClock();
		clock.setInstant(NOW);
		GithubAppJwt jwt = new GithubAppJwt("test-client-id", PemPrivateKeys.parse(GithubAppTestKeys.pkcs1Pem()), clock);

		String token = jwt.create();

		String[] parts = token.split("\\.");
		assertThat(parts).hasSize(3);
		JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(parts[0]));
		JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
		assertThat(header.get("alg").asString()).isEqualTo("RS256");
		assertThat(header.get("typ").asString()).isEqualTo("JWT");
		assertThat(claims.get("iat").asLong()).isEqualTo(NOW.getEpochSecond() - 60);
		assertThat(claims.get("exp").asLong()).isEqualTo(NOW.getEpochSecond() + 9 * 60);
		assertThat(claims.get("exp").asLong() - NOW.getEpochSecond()).isLessThanOrEqualTo(600);
		assertThat(claims.get("iss").asString()).isEqualTo("test-client-id");
		assertThat(parts[0] + parts[1] + parts[2]).doesNotContain("=");

		Signature verifier = Signature.getInstance("SHA256withRSA");
		verifier.initVerify(GithubAppTestKeys.publicKey());
		verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
		assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
	}

	@Test
	@DisplayName("S07: App JWT는 주입된 Clock의 시각으로 만든다")
	void usesInjectedClock() {
		MutableClock clock = new MutableClock();
		clock.setInstant(NOW);
		GithubAppJwt jwt = new GithubAppJwt("test-client-id", PemPrivateKeys.parse(GithubAppTestKeys.pkcs1Pem()), clock);
		String first = jwt.create();

		clock.setInstant(NOW.plusSeconds(120));
		JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(jwt.create().split("\\.")[1]));

		assertThat(claims.get("iat").asLong()).isEqualTo(NOW.getEpochSecond() + 60);
		assertThat(jwt.create()).isNotEqualTo(first);
	}

}
