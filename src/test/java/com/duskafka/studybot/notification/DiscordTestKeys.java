package com.duskafka.studybot.notification;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 디스코드 웹훅 URL 암호화 키를 테스트 안에서 만든다 (S14, infra.md 9.6 '디스코드 웹훅 URL 암호화 키'). JVM마다 지금 키와 옛 키 한 쌍이다.
 * 실제 키가 아니다. 모든 통합 테스트 컨텍스트가 같은 값을 받는다 ({@code DiscordTestConfiguration}).
 */
public final class DiscordTestKeys {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final String CURRENT = generate();

	private static final String PREVIOUS = generate();

	private DiscordTestKeys() {
	}

	/** 지금 키 (AES-256, 32바이트의 base64). */
	public static String current() {
		return CURRENT;
	}

	/** 교체 전 옛 키. */
	public static String previous() {
		return PREVIOUS;
	}

	/** 새 키 하나. */
	public static String generate() {
		byte[] key = new byte[32];
		RANDOM.nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}

}
