package com.duskafka.studybot.github.api;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/**
 * GitHub App으로 인증하는 JWT (S07 만들 것 4). 설치 토큰을 발급받을 때만 쓴다.
 *
 * <p>GitHub 문서대로 RS256으로 서명하고, {@code iat}는 시계 차이를 감안해 60초 전, {@code exp}는 10분을 넘지 않게 9분 뒤,
 * {@code iss}는 client ID(권장)다
 * (<a href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app">GitHub 문서</a>).
 * 라이브러리 없이 JDK {@link Signature}로 만든다 (S07 PR 판단 기록). 만든 값은 로그에 남기지 않는다.
 */
public class GithubAppJwt {

	static final Duration ISSUED_AT_SKEW = Duration.ofSeconds(60);

	static final Duration LIFETIME = Duration.ofMinutes(9);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

	private static final String HEADER = BASE64URL
		.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

	private final String clientId;

	private final PrivateKey privateKey;

	private final Clock clock;

	public GithubAppJwt(String clientId, PrivateKey privateKey, Clock clock) {
		this.clientId = Objects.requireNonNull(clientId, "clientId");
		this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public String create() {
		Instant now = this.clock.instant();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iat", now.minus(ISSUED_AT_SKEW).getEpochSecond());
		claims.put("exp", now.plus(LIFETIME).getEpochSecond());
		claims.put("iss", this.clientId);
		String signingInput = HEADER + "." + BASE64URL.encodeToString(JSON.writeValueAsBytes(claims));
		try {
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(this.privateKey);
			signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
			return signingInput + "." + BASE64URL.encodeToString(signature.sign());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("App JWT에 서명하지 못했어요", ex);
		}
	}

	/** 키가 찍히지 않게 가린다. */
	@Override
	public String toString() {
		return "GithubAppJwt[iss=" + this.clientId + "]";
	}

}
