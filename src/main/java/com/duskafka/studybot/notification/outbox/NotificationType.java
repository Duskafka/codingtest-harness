package com.duskafka.studybot.notification.outbox;

/**
 * 알림 종류 (5.8 '알림 종류' 표, glossary {@code Notification}). S13이 세 종류, S14가 디스코드 현황·결과 알림·디스코드 꺼짐 알림을 더했다
 * (V12). P2·P3 종류는 그 단계가 CHECK와 함께 새 마이그레이션으로 더한다.
 */
public enum NotificationType {

	/** 마감 전 알림: 아직 못 푼 멤버에게 (S13). */
	REMINDER,

	/** 공유 유도: 못 푼 멤버가 있으면 방장에게 (S13). */
	SHARE_PROMPT,

	/** 규칙 변경: 멤버에게 (S13). */
	RULE_CHANGED,

	/** 디스코드 현황 게시: 알림 시각에 디스코드를 쓰는 스터디 채널로 (S14). */
	DISCORD_STATUS,

	/** 판정 결과: 판정이 끝난 날의 결과를 디스코드 채널 또는 방장 웹 푸시로 (S14, 결정 제안서 010). */
	DAILY_RESULT,

	/**
	 * 디스코드 웹훅이 지워져(404) 게시를 끈 것을 방장에게 웹 푸시로 (S14 만들 것 4 '방장에게 알린다'). 5.8 표에 없는 종류다 (S14 PR 스펙 변경
	 * 제안).
	 */
	DISCORD_DISABLED

}
