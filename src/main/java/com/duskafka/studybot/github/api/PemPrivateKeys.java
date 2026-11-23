package com.duskafka.studybot.github.api;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub App private key(PEM)를 읽는다 (S07 만들 것 4). 라이브러리를 더하지 않고 JDK로만 읽는다 (S07 PR 판단 기록).
 *
 * <ul>
 * <li>{@code BEGIN RSA PRIVATE KEY}: PKCS#1. GitHub가 내려주는 형식이다
 * (<a href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/managing-private-keys-for-github-apps">GitHub 문서</a>).
 * JDK는 PKCS#8만 읽으므로 PKCS#8 머리를 씌운다.</li>
 * <li>{@code BEGIN PRIVATE KEY}: PKCS#8 ({@code openssl pkcs8 -topk8 -nocrypt}로 바꾼 것).</li>
 * </ul>
 *
 * 암호를 건 키는 받지 않는다. 실패해도 예외 메시지와 원인에 키 내용을 싣지 않는다.
 */
public final class PemPrivateKeys {

	private static final Pattern PEM = Pattern
		.compile("-----BEGIN ([A-Z ]+)-----([A-Za-z0-9+/=\\s]*)-----END \\1-----");

	/** PKCS#8 PrivateKeyInfo의 version(0)과 AlgorithmIdentifier(rsaEncryption, NULL). */
	private static final byte[] PKCS8_RSA_PREFIX = { 0x02, 0x01, 0x00, 0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48,
			(byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00 };

	private static final String FAILURE = "GitHub App private key를 읽지 못했어요. 암호 없는 RSA PEM(PKCS#1·PKCS#8)만 받아요";

	private PemPrivateKeys() {
	}

	public static PrivateKey parse(String pem) {
		// 환경 변수에 넣을 때 줄바꿈을 \n 두 글자로 쓰는 경우가 많다.
		String text = pem.replace("\\n", "\n");
		Matcher matcher = PEM.matcher(text);
		if (!matcher.find()) {
			throw new IllegalArgumentException(FAILURE);
		}
		String type = matcher.group(1);
		byte[] der;
		try {
			der = Base64.getMimeDecoder().decode(matcher.group(2));
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException(FAILURE);
		}
		byte[] pkcs8 = switch (type) {
			case "RSA PRIVATE KEY" -> wrapPkcs1(der);
			case "PRIVATE KEY" -> der;
			default -> throw new IllegalArgumentException(FAILURE);
		};
		try {
			return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
		}
		catch (GeneralSecurityException | RuntimeException ex) {
			// 원인 메시지에 키 바이트가 섞일 수 있어 원인을 붙이지 않는다.
			throw new IllegalArgumentException(FAILURE);
		}
	}

	/** PKCS#1 RSAPrivateKey를 PKCS#8 PrivateKeyInfo로 감싼다: SEQUENCE { version, algorithm, OCTET STRING { pkcs1 } }. */
	private static byte[] wrapPkcs1(byte[] pkcs1) {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.writeBytes(PKCS8_RSA_PREFIX);
		body.write(0x04);
		body.writeBytes(derLength(pkcs1.length));
		body.writeBytes(pkcs1);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(0x30);
		out.writeBytes(derLength(body.size()));
		out.writeBytes(body.toByteArray());
		return out.toByteArray();
	}

	private static byte[] derLength(int length) {
		if (length < 0x80) {
			return new byte[] { (byte) length };
		}
		int bytes = (Integer.SIZE - Integer.numberOfLeadingZeros(length) + 7) / 8;
		byte[] encoded = new byte[bytes + 1];
		encoded[0] = (byte) (0x80 | bytes);
		for (int i = 0; i < bytes; i++) {
			encoded[bytes - i] = (byte) (length >>> (8 * i));
		}
		return encoded;
	}

}
