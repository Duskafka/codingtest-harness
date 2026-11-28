package com.duskafka.studybot.notification.discord;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 디스코드 채널 웹훅 설정 (S14 만들 것 1~4, infra.md 9.6 '디스코드 웹훅 URL 암호화 키', security.md 9.7).
 *
 * @param apiBaseUrl 게시할 디스코드 API 주소. 등록은 공식 주소만 받고(T27) 보낼 때는 여기에 웹훅 ID·토큰 경로를 붙인다. 테스트만 바꾼다
 * @param encryption 웹훅 URL 암호화 키. 환경 변수로만 받고 기본값이 없다
 */
@ConfigurationProperties("studybot.discord")
public record DiscordProperties(@DefaultValue("https://discord.com/api") URI apiBaseUrl, Encryption encryption) {

	/**
	 * 웹훅 URL 암호화 키 (AES-256, 32바이트의 base64).
	 *
	 * @param key 지금 키. 새로 저장하는 주소는 이 키로 암호화한다
	 * @param previousKey 교체 중에만 넣는 옛 키. 옛 키로 저장된 주소도 풀고, 기동할 때 지금 키로 다시 암호화한다. 비어 있으면 없음
	 */
	public record Encryption(String key, @Nullable String previousKey) {

		@Override
		public String toString() {
			return "Encryption[key=***, previousKey=" + (this.previousKey == null || this.previousKey.isBlank() ? "(없음)" : "***")
					+ "]";
		}

	}

}
