package com.duskafka.studybot.github.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * GitHub 웹훅 서명 확인 (S06 만들 것 2, pseudocode '서명 검증 (Java)', security.md 9.7 '받는 요청').
 *
 * <p>{@code X-Hub-Signature-256 = "sha256=" + hex(HMAC-SHA256(시크릿, 원본 바이트))} [S36]. 받은 바이트 그대로 계산하고,
 * {@link MessageDigest#isEqual}로 상수 시간 비교한다. 시크릿을 교체하는 동안에는 옛 시크릿도 받는다 (infra.md 9.6).
 * 시크릿은 UTF-8 바이트로 쓴다 (GitHub App 설정에 적는 문자열 그대로).
 */
public final class GithubSignatureVerifier {

	static final String PREFIX = "sha256=";

	private static final String ALGORITHM = "HmacSHA256";

	private final List<SecretKeySpec> keys;

	/**
	 * @param secret 현재 시크릿. 비어 있으면 안 된다
	 * @param previousSecret 교체 중인 옛 시크릿. 교체 중이 아니면 {@code null}이나 빈 값
	 */
	public GithubSignatureVerifier(String secret, @Nullable String previousSecret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("웹훅 시크릿이 비어 있어요");
		}
		List<SecretKeySpec> configured = new ArrayList<>();
		configured.add(key(secret));
		if (previousSecret != null && !previousSecret.isBlank()) {
			configured.add(key(previousSecret));
		}
		this.keys = List.copyOf(configured);
	}

	/** 서명 헤더가 본문과 맞으면 {@code true}. 헤더가 없거나, 형식이 틀리거나(16진수 아님 포함), 어느 시크릿과도 맞지 않으면 {@code false}. */
	public boolean verify(byte[] body, @Nullable String signatureHeader) {
		if (signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
			return false;
		}
		byte[] actual;
		try {
			actual = HexFormat.of().parseHex(signatureHeader, PREFIX.length(), signatureHeader.length());
		}
		catch (IllegalArgumentException ex) {
			return false; // 16진수가 아니거나 자릿수가 홀수
		}
		// 어느 시크릿과 맞았는지가 걸린 시간으로 드러나지 않게, 맞아도 끝까지 계산한다.
		boolean matched = false;
		for (SecretKeySpec key : keys) {
			matched |= MessageDigest.isEqual(hmac(key, body), actual);
		}
		return matched;
	}

	private static SecretKeySpec key(String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	private static byte[] hmac(SecretKeySpec key, byte[] body) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return mac.doFinal(body);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HMAC-SHA256을 계산할 수 없어요", ex);
		}
	}

}
