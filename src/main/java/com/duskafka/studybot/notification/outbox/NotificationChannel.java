package com.duskafka.studybot.notification.outbox;

/** 알림 채널 (5.8 '알림 종류' 표의 채널, D18). */
public enum NotificationChannel {

	/** 개인 웹 푸시 (PWA). 받는 사람이 늘 있다. */
	WEB_PUSH,

	/** 디스코드 채널 웹훅 (S14). */
	DISCORD

}
