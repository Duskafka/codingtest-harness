package com.duskafka.studybot.notification;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 브라우저(구독) 쪽을 흉내 낸다 (S13). 라이브러리를 믿지 않고 받은 요청을 JDK만으로 검증한다 (결정 제안서 009 '검증 방법').
 *
 * <ul>
 * <li>구독 키: P-256 키 쌍(p256dh)과 16바이트 auth를 테스트 안에서 만든다.</li>
 * <li>{@link #decrypt}: RFC 8291(+ RFC 8188 {@code aes128gcm})대로 본문을 푼다. 레코드 하나, 마지막 레코드 구분자 0x02.</li>
 * <li>{@link #verifyVapid}: {@code Authorization: vapid t=<JWT>, k=<공개키>}의 ES256 서명을 VAPID 공개키로 확인하고 클레임을 돌려준다
 * (RFC 8292).</li>
 * </ul>
 */
public final class PushReceiver {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final KeyPair keys;

	private final byte[] auth;

	private PushReceiver(KeyPair keys, byte[] auth) {
		this.keys = keys;
		this.auth = auth;
	}

	public static PushReceiver create() {
		byte[] auth = new byte[16];
		new SecureRandom().nextBytes(auth);
		return new PushReceiver(WebPushTestKeys.generate(), auth);
	}

	/** 브라우저가 주는 {@code keys.p256dh} (base64url). */
	public String p256dh() {
		return WebPushTestKeys.base64Url(publicPoint());
	}

	/** 브라우저가 주는 {@code keys.auth} (base64url). */
	public String auth() {
		return WebPushTestKeys.base64Url(this.auth);
	}

	private byte[] publicPoint() {
		return WebPushTestKeys.uncompressed((ECPublicKey) this.keys.getPublic());
	}

	/** {@code aes128gcm} 본문을 풀어 평문(UTF-8)을 돌려준다. 풀리지 않으면 예외. */
	public String decrypt(byte[] body) {
		try {
			ByteBuffer buffer = ByteBuffer.wrap(body);
			byte[] salt = new byte[16];
			buffer.get(salt);
			buffer.getInt(); // 레코드 크기
			int idLength = buffer.get() & 0xff;
			byte[] senderPublic = new byte[idLength];
			buffer.get(senderPublic);
			byte[] ciphertext = new byte[buffer.remaining()];
			buffer.get(ciphertext);

			KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
			agreement.init(this.keys.getPrivate());
			agreement.doPhase(WebPushTestKeys.fromUncompressed(senderPublic), true);
			byte[] ecdhSecret = agreement.generateSecret();
			byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] { 0 }, publicPoint(),
					senderPublic);
			byte[] ikm = hmac(hmac(this.auth, ecdhSecret), keyInfo, new byte[] { 1 });
			byte[] prk = hmac(salt, ikm);
			byte[] cek = Arrays.copyOf(
					hmac(prk, "Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[] { 0, 1 }), 16);
			byte[] nonce = Arrays
				.copyOf(hmac(prk, "Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[] { 0, 1 }), 12);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
			byte[] padded = cipher.doFinal(ciphertext);
			int end = padded.length - 1;
			while (end >= 0 && padded[end] == 0) {
				end--;
			}
			if (end < 0 || padded[end] != 2) {
				throw new IllegalStateException("마지막 레코드 구분자(0x02)가 없어요");
			}
			return new String(padded, 0, end, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("본문을 풀지 못했어요", ex);
		}
	}

	/**
	 * VAPID 헤더를 검증하고 JWT 클레임을 돌려준다. {@code k}가 VAPID 공개키가 아니거나 서명이 맞지 않으면 예외.
	 *
	 * @param authorization {@code Authorization} 헤더 값
	 */
	public static JsonNode verifyVapid(String authorization) {
		if (!authorization.startsWith("vapid ")) {
			throw new IllegalStateException("vapid 방식이 아니에요: " + authorization.split(" ")[0]);
		}
		String token = null;
		String key = null;
		for (String part : authorization.substring("vapid ".length()).split(",")) {
			String trimmed = part.trim();
			if (trimmed.startsWith("t=")) {
				token = trimmed.substring(2);
			}
			else if (trimmed.startsWith("k=")) {
				key = trimmed.substring(2);
			}
		}
		if (token == null || key == null) {
			throw new IllegalStateException("t나 k가 없어요");
		}
		if (!key.equals(WebPushTestKeys.vapidPublicKeyBase64Url())) {
			throw new IllegalStateException("k가 VAPID 공개키가 아니에요");
		}
		String[] parts = token.split("\\.");
		try {
			JsonNode header = JSON.readTree(WebPushTestKeys.fromBase64Url(parts[0]));
			if (!"ES256".equals(header.path("alg").asString())) {
				throw new IllegalStateException("ES256이 아니에요");
			}
			Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
			verifier.initVerify(WebPushTestKeys.vapidPublicKey());
			verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
			if (!verifier.verify(WebPushTestKeys.fromBase64Url(parts[2]))) {
				throw new IllegalStateException("JWT 서명이 맞지 않아요");
			}
			return JSON.readTree(WebPushTestKeys.fromBase64Url(parts[1]));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static byte[] hmac(byte[] key, byte[]... data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		for (byte[] chunk : data) {
			mac.update(chunk);
		}
		return mac.doFinal();
	}

	private static byte[] concat(byte[]... parts) {
		int length = 0;
		for (byte[] part : parts) {
			length += part.length;
		}
		byte[] out = new byte[length];
		int offset = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, out, offset, part.length);
			offset += part.length;
		}
		return out;
	}

}
