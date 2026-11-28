package com.duskafka.studybot.notification.discord;

import com.duskafka.studybot.notification.card.Cards.ResultCard;
import com.duskafka.studybot.notification.card.Cards.StatusCard;

/**
 * 카드({@link com.duskafka.studybot.notification.card.Cards})를 디스코드 임베드로 바꾼다 (5.9 '디스코드 임베드 메시지 (같은 내용, 자동
 * 게시)'). 디스코드 마크다운 문자는 글자 그대로 보이게 앞에 {@code \}를 붙인다. 스터디 이름(사용자가 정함)에 {@code **}·{@code [텍스트](주소)}를
 * 넣어 모양을 바꾸거나 가짜 링크를 만들지 못하게 하려는 것이다. 멘션은 {@link DiscordMessage}의 {@code allowed_mentions}가 막는다.
 */
final class DiscordMessages {

	static final int STATUS_COLOR = 0x3B82F6;

	static final int RESULT_COLOR = 0x10B981;

	static final int TEST_COLOR = 0x6B7280;

	private DiscordMessages() {
	}

	static DiscordMessage status(StatusCard card) {
		return new DiscordMessage(escape(card.title()), escape(card.summary()), card.url(), STATUS_COLOR);
	}

	static DiscordMessage result(ResultCard card) {
		return new DiscordMessage(escape(card.title()), escape(card.summary()), card.url(), RESULT_COLOR);
	}

	/** 테스트 게시 (S14 만들 것 2). */
	static DiscordMessage test(String studyName) {
		return new DiscordMessage(escape("[" + studyName + "] 디스코드 연결 테스트"),
				"이 채널로 마감 전 인증 현황과 판정 결과를 보내요.", null, TEST_COLOR);
	}

	/** 디스코드 마크다운 문자 앞에 {@code \}를 붙인다. */
	static String escape(String text) {
		StringBuilder escaped = new StringBuilder(text.length());
		for (char ch : text.toCharArray()) {
			if ("\\*_~`|>[]".indexOf(ch) >= 0) {
				escaped.append('\\');
			}
			escaped.append(ch);
		}
		return escaped.toString();
	}

}
