package com.duskafka.studybot.github;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 테스트에서 GitHub처럼 서명한다: {@code X-Hub-Signature-256 = "sha256=" + hex(HMAC-SHA256(시크릿, 본문))}
 * (external-systems GitHub 행 [S36]). 앱의 검증기와 따로 계산해서, 검증기가 틀려도 테스트가 따라 틀리지 않게 한다.
 */
public final class WebhookSigner {

	/** 테스트 설정(config/application.yaml)의 현재 시크릿. 가짜 값이다. */
	public static final String SECRET = "test-webhook-secret";

	/** 테스트 설정의 옛 시크릿 (교체 중). 가짜 값이다. */
	public static final String PREVIOUS_SECRET = "test-webhook-secret-previous";

	private WebhookSigner() {
	}

	public static String sign(String secret, byte[] body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static String sign(byte[] body) {
		return sign(SECRET, body);
	}

}
