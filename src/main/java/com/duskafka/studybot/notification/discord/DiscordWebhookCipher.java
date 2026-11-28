package com.duskafka.studybot.notification.discord;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/**
 * 디스코드 웹훅 URL을 앱 수준에서 암호화한다 (S14 만들 것 1, data-model {@code study_channels}, infra.md 9.6 '디스코드 웹훅 URL 암호화
 * 키'). JDK만 쓴다.
 *
 * <ul>
 * <li>AES-256-GCM, 무작위 12바이트 nonce, 128비트 태그. 스터디 ID를 AAD로 묶어 다른 스터디 행으로 옮긴 암호문은 풀리지 않는다.</li>
 * <li>저장 형식: {@code v1:{키 ID}:{base64url(nonce | 암호문 | 태그)}}. 키 ID는 키의 SHA-256 앞 4바이트(16진수 8자리)다. 키 자체는 드러나지
 * 않고, 어느 키로 암호화했는지 알 수 있다.</li>
 * <li>키 교체: 새 키를 지금 키로, 옛 키를 옛 키로 넣고 띄운다. 옛 키로 저장된 주소도 풀고, 기동할 때 지금 키로 다시 암호화한다
 * ({@link DiscordKeyRotation}). 다 바뀌면 옛 키를 뺀다 (S14 PR 판단 기록 '암호화 방식과 키 교체 절차').</li>
 * </ul>
 *
 * <p>키가 비었거나 base64 32바이트가 아니면 만들 수 없다(기동 실패). 풀 수 없는 암호문(모르는 키, 바뀐 값, 다른 스터디)은
 * {@link IllegalStateException}이다. 메시지에 키·평문·암호문을 싣지 않는다.
 */
public final class DiscordWebhookCipher {

	static final String VERSION = "v1";

	private static final int KEY_BYTES = 32;

	private static final int NONCE_BYTES = 12;

	private static final int TAG_BITS = 128;

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private final SecureRandom random = new SecureRandom();

	private final Key current;

	private final @Nullable Key previous;

	/**
	 * @param key 지금 키 (base64, 32바이트)
	 * @param previousKey 옛 키. 비었으면 없음
	 */
	public DiscordWebhookCipher(String key, @Nullable String previousKey) {
		this.current = Key.parse(key, "지금 키");
		this.previous = previousKey == null || previousKey.isBlank() ? null : Key.parse(previousKey, "옛 키");
		if (this.previous != null && this.previous.id().equals(this.current.id())) {
			throw new IllegalStateException("디스코드 웹훅 URL 암호화의 옛 키가 지금 키와 같아요");
		}
	}

	/** 이 스터디의 웹훅 URL을 지금 키로 암호화한다. */
	public String encrypt(long studyId, String plaintext) {
		byte[] nonce = new byte[NONCE_BYTES];
		this.random.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, this.current.spec(), new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(aad(studyId));
			byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] body = ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
			return currentPrefix() + Base64.getUrlEncoder().withoutPadding().encodeToString(body);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("디스코드 웹훅 URL을 암호화하지 못했어요", ex);
		}
	}

	/** 이 스터디의 암호문을 푼다. 지금 키나 옛 키로 만든 것만 풀린다. */
	public String decrypt(long studyId, String ciphertext) {
		String[] parts = ciphertext.split(":", 3);
		if (parts.length != 3 || !VERSION.equals(parts[0])) {
			throw new IllegalStateException("디스코드 웹훅 URL 암호문 형식이 아니에요");
		}
		Key key = keyOf(parts[1]);
		byte[] body;
		try {
			body = Base64.getUrlDecoder().decode(parts[2]);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("디스코드 웹훅 URL 암호문 형식이 아니에요");
		}
		if (body.length <= NONCE_BYTES + TAG_BITS / 8) {
			throw new IllegalStateException("디스코드 웹훅 URL 암호문 형식이 아니에요");
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key.spec(), new GCMParameterSpec(TAG_BITS, body, 0, NONCE_BYTES));
			cipher.updateAAD(aad(studyId));
			byte[] plain = cipher.doFinal(body, NONCE_BYTES, body.length - NONCE_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("디스코드 웹훅 URL 암호문을 풀지 못했어요 (키가 다르거나 값이 바뀜)");
		}
	}

	/** 지금 키로 만든 암호문인지. 아니면 키 교체 뒤 다시 암호화할 대상이다. */
	public boolean isCurrent(String ciphertext) {
		return ciphertext.startsWith(currentPrefix());
	}

	/** 지금 키의 ID (16진수 8자리). */
	public String currentKeyId() {
		return this.current.id();
	}

	/** 지금 키로 만든 암호문의 앞부분 {@code v1:{키 ID}:}. */
	public String currentPrefix() {
		return VERSION + ":" + this.current.id() + ":";
	}

	private Key keyOf(String id) {
		if (this.current.id().equals(id)) {
			return this.current;
		}
		if (this.previous != null && this.previous.id().equals(id)) {
			return this.previous;
		}
		throw new IllegalStateException("디스코드 웹훅 URL 암호문의 키를 몰라요 (키 ID " + id + ")");
	}

	private static byte[] aad(long studyId) {
		return ("study_channels:" + studyId).getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public String toString() {
		return "DiscordWebhookCipher[currentKeyId=" + this.current.id() + ", previousKeyId="
				+ (this.previous == null ? "(없음)" : this.previous.id()) + "]";
	}

	private record Key(String id, SecretKeySpec spec) {

		Key {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(spec, "spec");
		}

		static Key parse(@Nullable String base64, String label) {
			if (base64 == null || base64.isBlank()) {
				throw new IllegalStateException("디스코드 웹훅 URL 암호화 " + label + "가 없어요");
			}
			byte[] bytes;
			try {
				bytes = Base64.getDecoder().decode(base64.strip());
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalStateException("디스코드 웹훅 URL 암호화 " + label + "가 base64가 아니에요");
			}
			if (bytes.length != KEY_BYTES) {
				throw new IllegalStateException("디스코드 웹훅 URL 암호화 " + label + "는 32바이트여야 해요");
			}
			try {
				byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
				return new Key(HexFormat.of().formatHex(digest, 0, 4), new SecretKeySpec(bytes, "AES"));
			}
			catch (NoSuchAlgorithmException ex) {
				throw new IllegalStateException(ex);
			}
		}

		@Override
		public String toString() {
			return "Key[id=" + this.id + "]";
		}

	}

}
