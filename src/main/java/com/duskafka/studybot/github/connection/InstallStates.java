package com.duskafka.studybot.github.connection;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 설치 주소에 붙이는 서명된 1회용 state (5.2, api.md 4.4 {@code GET /api/github/install-url}, S07 '판단 기록이 필요한 곳').
 *
 * <ul>
 * <li><b>모양:</b> {@code base64url(사용자 ID.만료 시각.nonce) + "." + base64url(HMAC-SHA256)}. 서명으로 사용자와 만료 시각을 묶는다.
 * 유효 시간은 30분이다 (레포를 고르는 시간을 넉넉히 둔다).</li>
 * <li><b>서명 키:</b> 앱이 뜰 때 무작위로 만든 메모리 키. 새 비밀값을 운영에 두지 않는다. 다시 뜨면 기다리던 state는 무효가 되지만,
 * 연결은 state와 상관없이 서명된 웹훅의 설치자 정보로 하므로(T21) 안내 문구만 달라진다.</li>
 * <li><b>1회용:</b> 발급한 nonce를 세션(Spring Session JDBC)에 두고, 확인하면 지운다. 세션마다 마지막에 받은 것 하나만 유효하다.</li>
 * </ul>
 *
 * state는 연결 여부를 정하지 않는다. Setup URL로 돌아온 요청이 이 사용자가 시작한 설치인지 안내에만 쓴다.
 */
@Component
class InstallStates {

	static final Duration VALIDITY = Duration.ofMinutes(30);

	static final String SESSION_ATTRIBUTE = InstallStates.class.getName() + ".nonce";

	private static final int MAX_STATE_LENGTH = 512;

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final SecureRandom random = new SecureRandom();

	private final SecretKeySpec key;

	private final Clock clock;

	InstallStates(Clock clock) {
		this.clock = clock;
		byte[] keyBytes = new byte[32];
		this.random.nextBytes(keyBytes);
		this.key = new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	/** 새 state를 만들고 세션에 nonce를 남긴다. 이 세션의 이전 state는 무효가 된다. */
	String issue(long userId, HttpSession session) {
		byte[] nonceBytes = new byte[16];
		this.random.nextBytes(nonceBytes);
		String nonce = ENCODER.encodeToString(nonceBytes);
		long expiresAt = this.clock.instant().plus(VALIDITY).getEpochSecond();
		String signed = ENCODER.encodeToString((userId + "." + expiresAt + "." + nonce).getBytes(StandardCharsets.US_ASCII));
		session.setAttribute(SESSION_ATTRIBUTE, nonce);
		return signed + "." + ENCODER.encodeToString(sign(signed));
	}

	/** 이 사용자가 이 세션에서 받은 유효한 state면 소모하고 {@code true}. 그 밖에는 아무것도 바꾸지 않고 {@code false}. */
	boolean consume(long userId, @Nullable String state, HttpSession session) {
		if (state == null || state.length() > MAX_STATE_LENGTH) {
			return false;
		}
		int dot = state.indexOf('.');
		if (dot <= 0) {
			return false;
		}
		String signed = state.substring(0, dot);
		String[] parts;
		byte[] signature;
		try {
			signature = DECODER.decode(state.substring(dot + 1));
			parts = new String(DECODER.decode(signed), StandardCharsets.US_ASCII).split("\\.", 3);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
		if (!MessageDigest.isEqual(sign(signed), signature) || parts.length != 3) {
			return false;
		}
		try {
			if (Long.parseLong(parts[0]) != userId
					|| !this.clock.instant().isBefore(Instant.ofEpochSecond(Long.parseLong(parts[1])))) {
				return false;
			}
		}
		catch (NumberFormatException ex) {
			return false;
		}
		if (!(session.getAttribute(SESSION_ATTRIBUTE) instanceof String pending) || !MessageDigest
			.isEqual(pending.getBytes(StandardCharsets.US_ASCII), parts[2].getBytes(StandardCharsets.US_ASCII))) {
			return false;
		}
		session.removeAttribute(SESSION_ATTRIBUTE);
		return true;
	}

	private byte[] sign(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(this.key);
			return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HMAC-SHA256을 쓸 수 없어요", ex);
		}
	}

}
