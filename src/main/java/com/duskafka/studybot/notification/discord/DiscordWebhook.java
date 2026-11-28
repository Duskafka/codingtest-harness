package com.duskafka.studybot.notification.discord;

import java.util.Objects;

/**
 * 디스코드 채널 웹훅 하나: 웹훅 ID와 토큰 (S14). 토큰을 아는 사람은 누구나 그 채널에 글을 올릴 수 있어 비밀로 다룬다 (2.7). 로그·응답에 내보내지
 * 않는다. {@link #toString()}도 토큰을 가린다.
 *
 * @param id 웹훅 ID (숫자)
 * @param token 웹훅 토큰
 */
public record DiscordWebhook(String id, String token) {

	public DiscordWebhook {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(token, "token");
	}

	/** 저장할 정규화한 주소. 받은 주소가 다른 공식 도메인·API 버전이어도 이 모양으로 저장한다. */
	public String canonicalUrl() {
		return "https://discord.com/api/webhooks/" + this.id + "/" + this.token;
	}

	@Override
	public String toString() {
		return "DiscordWebhook[id=" + this.id + ", token=***]";
	}

}
