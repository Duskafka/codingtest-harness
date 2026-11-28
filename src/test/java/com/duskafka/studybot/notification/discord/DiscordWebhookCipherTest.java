package com.duskafka.studybot.notification.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.duskafka.studybot.notification.DiscordTestKeys;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 디스코드 웹훅 URL 암호화 (S14 수용 기준 '웹훅 URL이 DB에 평문으로 남지 않는다', 판단 기록 '암호화 방식과 키 교체 절차', infra.md 9.6).
 */
class DiscordWebhookCipherTest {

	private static final String URL = "https://discord.com/api/webhooks/1300000000000000001/abcDEF_ghi-JKL";

	@Test
	@DisplayName("암호화한 값은 v1:키ID:본문 형식이고 평문을 담지 않으며, 같은 스터디로 풀면 원래 주소다")
	void roundTrip() {
		DiscordWebhookCipher cipher = new DiscordWebhookCipher(DiscordTestKeys.generate(), null);

		String encrypted = cipher.encrypt(7L, URL);

		assertThat(encrypted).matches("v1:[0-9a-f]{8}:[A-Za-z0-9_-]+")
			.doesNotContain("discord")
			.doesNotContain("1300000000000000001")
			.doesNotContain("abcDEF");
		assertThat(encrypted.split(":")[1]).isEqualTo(cipher.currentKeyId());
		assertThat(cipher.decrypt(7L, encrypted)).isEqualTo(URL);
	}

	@Test
	@DisplayName("같은 주소도 암호화할 때마다 다르다 (nonce가 무작위)")
	void nonceIsRandom() {
		DiscordWebhookCipher cipher = new DiscordWebhookCipher(DiscordTestKeys.generate(), null);

		assertThat(cipher.encrypt(7L, URL)).isNotEqualTo(cipher.encrypt(7L, URL));
	}

	@Test
	@DisplayName("다른 스터디의 행으로 옮긴 암호문은 풀리지 않는다 (스터디 ID를 AAD로 묶음)")
	void otherStudyCannotDecrypt() {
		DiscordWebhookCipher cipher = new DiscordWebhookCipher(DiscordTestKeys.generate(), null);
		String encrypted = cipher.encrypt(7L, URL);

		assertThatThrownBy(() -> cipher.decrypt(8L, encrypted)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("암호문을 한 글자라도 바꾸면 풀리지 않는다 (GCM 태그)")
	void tamperedFails() {
		DiscordWebhookCipher cipher = new DiscordWebhookCipher(DiscordTestKeys.generate(), null);
		String encrypted = cipher.encrypt(7L, URL);
		// 본문 가운데 글자를 바꾼다. 마지막 글자는 쓰지 않는 비트만 담을 수 있어(패딩 없는 base64) 바꿔도 같은 바이트일 수 있다.
		int at = cipher.currentPrefix().length() + 20;
		char original = encrypted.charAt(at);
		String tampered = encrypted.substring(0, at) + (original == 'A' ? 'B' : 'A') + encrypted.substring(at + 1);

		assertThatThrownBy(() -> cipher.decrypt(7L, tampered)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("키 교체 중: 옛 키로 만든 암호문도 풀고, 지금 키가 아니라고 알려 준다. 모르는 키의 암호문은 풀지 않는다")
	void previousKeyStillDecrypts() {
		String oldKey = DiscordTestKeys.generate();
		String newKey = DiscordTestKeys.generate();
		String byOldKey = new DiscordWebhookCipher(oldKey, null).encrypt(7L, URL);
		DiscordWebhookCipher rotating = new DiscordWebhookCipher(newKey, oldKey);

		assertThat(rotating.decrypt(7L, byOldKey)).isEqualTo(URL);
		assertThat(rotating.isCurrent(byOldKey)).isFalse();
		assertThat(rotating.isCurrent(rotating.encrypt(7L, URL))).isTrue();

		DiscordWebhookCipher afterRotation = new DiscordWebhookCipher(newKey, null);
		assertThatThrownBy(() -> afterRotation.decrypt(7L, byOldKey)).isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest(name = "[{0}]")
	@ValueSource(strings = { "", "   ", "not-base64!!", "c2hvcnQ=" })
	@DisplayName("키가 비었거나 base64 32바이트가 아니면 만들 수 없다 (앱이 뜨지 않음)")
	void invalidKeyRejected(String key) {
		assertThatThrownBy(() -> new DiscordWebhookCipher(key, null)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("옛 키가 지금 키와 같거나 형식이 틀리면 만들 수 없다. 비어 있으면 옛 키 없음이다")
	void invalidPreviousKeyRejected() {
		String key = DiscordTestKeys.generate();

		assertThatThrownBy(() -> new DiscordWebhookCipher(key, key)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new DiscordWebhookCipher(key, Base64.getEncoder().encodeToString(new byte[16])))
			.isInstanceOf(IllegalStateException.class);
		assertThat(new DiscordWebhookCipher(key, "").currentKeyId()).hasSize(8);
	}

	@Test
	@DisplayName("toString에 키가 드러나지 않는다")
	void toStringHidesKeys() {
		String key = DiscordTestKeys.generate();

		assertThat(new DiscordWebhookCipher(key, null).toString()).doesNotContain(key);
	}

}
