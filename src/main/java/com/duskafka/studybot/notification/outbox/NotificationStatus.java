package com.duskafka.studybot.notification.outbox;

/** 알림 상태 (glossary {@code Notification}, 5.8 상태 그림). */
public enum NotificationStatus {

	/** 발송 대기. 다음 시도 시각이 되면 발송기가 가져간다. */
	PENDING,

	/** 발송 중. 잠금 만료 시각이 지나면 발송 대기로 돌아간다. */
	SENDING,

	/** 보냈다 (웹 푸시는 기기 하나 이상이 받음). */
	SENT,

	/** 영구 실패 (구독 없음, 구독 만료, 다시 보내도 같은 거절). */
	FAILED,

	/** 보내기 직전에 보낼 까닭이 없어졌다 (그새 목표 달성·면제). */
	SKIPPED,

	/** 만료 시각이 지나 보내지 않았다. */
	EXPIRED

}
