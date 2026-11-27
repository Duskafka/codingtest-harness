package com.duskafka.studybot.notification.dispatch;

import com.duskafka.studybot.notification.outbox.NotificationChannel;
import com.duskafka.studybot.notification.persistence.NotificationQueue.ClaimedNotification;
import java.time.Instant;

/**
 * 채널 하나로 알림을 보낸다 (5.8). 발송기({@link NotificationDispatcher})가 트랜잭션 밖에서 부른다. 웹 푸시는 S13, 디스코드는 S14가
 * 구현한다.
 */
public interface NotificationSender {

	NotificationChannel channel();

	/**
	 * @param now 지금 시각 (TTL 계산 등)
	 */
	SendResult send(ClaimedNotification notification, Instant now);

}
