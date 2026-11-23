package com.duskafka.studybot.study.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 초대 토큰 (5.1 처리 순서 2). 32바이트 무작위 값을 base64url(패딩 없음, 43자)로 쓰고, DB에는 SHA-256 해시만 저장한다.
 * DB가 유출돼도 해시로는 초대 링크를 만들 수 없다. 토큰은 로그에 남기지 않는다.
 */
@Component
class InvitationTokens {

	private static final int TOKEN_BYTES = 32;

	private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final SecureRandom random = new SecureRandom();

	/** 새 토큰 원문. 응답에 한 번만 싣는다. */
	String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		this.random.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}

	/** 저장·조회에 쓰는 해시 (토큰 글자의 SHA-256, 32바이트). */
	byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256을 쓸 수 없어요", ex);
		}
	}

	/** 형식이 맞는 토큰이면 해시. 형식이 틀리면 찾아볼 필요도 없어 비운다. */
	Optional<byte[]> hashIfWellFormed(String token) {
		if (!TOKEN_FORMAT.matcher(token).matches()) {
			return Optional.empty();
		}
		return Optional.of(hash(token));
	}

}
