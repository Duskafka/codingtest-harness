package com.duskafka.studybot.notification.outbox;

/**
 * 알림 종류 (5.8 '알림 종류' 표, glossary {@code Notification}). S13이 만드는 세 종류만 있다. 디스코드 현황(DISCORD_STATUS)과 결과
 * 알림(DAILY_RESULT)은 S14가, P2·P3 종류는 그 단계가 더한다 (V11 CHECK도 새 마이그레이션으로 함께).
 */
public enum NotificationType {

	/** 마감 전 알림: 아직 못 푼 멤버에게 (S13). */
	REMINDER,

	/** 공유 유도: 못 푼 멤버가 있으면 방장에게 (S13). */
	SHARE_PROMPT,

	/** 규칙 변경: 멤버에게 (S13). */
	RULE_CHANGED

}
