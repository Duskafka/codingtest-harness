package com.duskafka.studybot.notification.discord;

import org.jspecify.annotations.Nullable;

/** 디스코드 웹훅 URL 암호화 (S14). 테스트를 먼저 컴파일하려는 뼈대다. */
public class DiscordWebhookCipher {

	public DiscordWebhookCipher(String key, @Nullable String previousKey) {
	}

	public String encrypt(long studyId, String plaintext) {
		throw new UnsupportedOperationException("S14");
	}

	public String decrypt(long studyId, String ciphertext) {
		throw new UnsupportedOperationException("S14");
	}

	public boolean isCurrent(String ciphertext) {
		throw new UnsupportedOperationException("S14");
	}

	public String currentKeyId() {
		throw new UnsupportedOperationException("S14");
	}

}
