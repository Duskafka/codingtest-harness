package com.duskafka.studybot.notification;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 테스트 안에서 만든 웹 푸시 키 (S13 '주의할 것': 테스트에는 테스트 안에서 만든 키를 쓴다). 실제 VAPID 키와 관계없다.
 *
 * <ul>
 * <li>VAPID 키 쌍은 JVM마다 한 번 만든다. 모든 통합 테스트 컨텍스트가 같은 값을 받는다 ({@code WebPushTestConfiguration}).</li>
 * <li>형식은 운영 환경 변수와 같다: 공개키는 압축하지 않은 P-256 점(65바이트)의 base64url, 개인키는 32바이트 스칼라의 base64url
 * (브라우저 {@code applicationServerKey}와 같은 형식).</li>
 * </ul>
 */
public final class WebPushTestKeys {

	/** P-256 공개키의 X.509(SubjectPublicKeyInfo) 머리. 뒤에 압축하지 않은 점 65바이트가 붙는다. */
	private static final byte[] P256_X509_PREFIX = HexFormat.of()
		.parseHex("3059301306072a8648ce3d020106082a8648ce3d030107034200");

	private static final KeyPair VAPID = generate();

	private WebPushTestKeys() {
	}

	/** 새 P-256 키 쌍. */
	public static KeyPair generate() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
			generator.initialize(new ECGenParameterSpec("secp256r1"));
			return generator.generateKeyPair();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public static ECPublicKey vapidPublicKey() {
		return (ECPublicKey) VAPID.getPublic();
	}

	/** 앱의 {@code studybot.webpush.vapid.public-key}에 넣는 값. */
	public static String vapidPublicKeyBase64Url() {
		return base64Url(uncompressed(vapidPublicKey()));
	}

	/** 앱의 {@code studybot.webpush.vapid.private-key}에 넣는 값. */
	public static String vapidPrivateKeyBase64Url() {
		return base64Url(scalar((ECPrivateKey) VAPID.getPrivate()));
	}

	/** 다른 키 쌍의 공개키(base64url). 공개키와 개인키가 맞지 않는 설정을 만들 때 쓴다. */
	public static String otherPublicKeyBase64Url() {
		return base64Url(uncompressed((ECPublicKey) generate().getPublic()));
	}

	/** 압축하지 않은 점 (0x04 || X || Y, 65바이트). */
	public static byte[] uncompressed(ECPublicKey key) {
		byte[] x509 = key.getEncoded();
		return Arrays.copyOfRange(x509, x509.length - 65, x509.length);
	}

	public static ECPublicKey fromUncompressed(byte[] point) {
		byte[] x509 = new byte[P256_X509_PREFIX.length + point.length];
		System.arraycopy(P256_X509_PREFIX, 0, x509, 0, P256_X509_PREFIX.length);
		System.arraycopy(point, 0, x509, P256_X509_PREFIX.length, point.length);
		try {
			return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(x509));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	public static String base64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static byte[] fromBase64Url(String value) {
		return Base64.getUrlDecoder().decode(value);
	}

	private static byte[] scalar(ECPrivateKey key) {
		byte[] raw = key.getS().toByteArray();
		byte[] fixed = new byte[32];
		int copy = Math.min(raw.length, 32);
		System.arraycopy(raw, raw.length - copy, fixed, 32 - copy, copy);
		if (new BigInteger(1, fixed).signum() == 0) {
			throw new IllegalStateException("개인키가 0이에요");
		}
		return fixed;
	}

}
