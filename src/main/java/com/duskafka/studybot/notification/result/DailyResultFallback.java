package com.duskafka.studybot.notification.result;

import com.duskafka.studybot.notification.outbox.NotificationChannel;
import com.duskafka.studybot.notification.outbox.NotificationOutbox;
import com.duskafka.studybot.notification.outbox.NotificationOutbox.NewWebPush;
import com.duskafka.studybot.notification.outbox.NotificationType;
import com.duskafka.studybot.notification.outbox.PushMessage;
import com.duskafka.studybot.notification.persistence.NotificationQueue.ClaimedNotification;
import com.duskafka.studybot.study.StudyProfiles;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 디스코드로 보내려던 결과 알림을 방장 웹 푸시로 대신 넣는다 (사람이 정함, 2026-11-28 PR #37 열린 질문 7 "방장 웹 푸시로 대신 보냄").
 *
 * <p>결과 알림({@code DAILY_RESULT}) 디스코드 행을 만든 뒤 보내기 전에 채널이 꺼지면(방장이 해제, 404·401·403으로 꺼짐) 그날 결과가 빠진다. 5.8
 * '알림 한 번이 중복되는 건 괜찮지만 빠지는 건 안 돼요'를 지키려고 같은 내용을 방장에게 웹 푸시로 보낸다.
 *
 * <ul>
 * <li>내용: 디스코드 행의 payload를 그대로 쓴다. 숫자만 담은 웹 푸시 모양이라 이름이 없다 ({@link DailyResultNotifier}).</li>
 * <li>키: {@code DAILY_RESULT:{study}:{day}:owner}. 디스코드 행의 키와 겹치지 않고, 여러 번 불러도 하나다.</li>
 * <li>받는 사람: 지금 방장({@code studies.owner_id}). 만료는 디스코드 행과 같다.</li>
 * <li>현황 게시({@code DISCORD_STATUS})는 대신 보내지 않는다. 방장에게는 공유 유도가 따로 간다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DailyResultFallback {

	private final StudyProfiles studyProfiles;

	private final NotificationOutbox outbox;

	private final JsonMapper jsonMapper;

	/**
	 * 디스코드 결과 행이면 방장 웹 푸시로 대신 넣는다. 그 밖의 행은 아무것도 하지 않는다.
	 *
	 * @return 새로 넣었는지
	 */
	@Transactional
	public boolean toOwner(ClaimedNotification notification) {
		LocalDate studyDay = notification.studyDay();
		if (!NotificationType.DAILY_RESULT.name().equals(notification.type())
				|| !NotificationChannel.DISCORD.name().equals(notification.channel()) || studyDay == null) {
			return false;
		}
		PushMessage message = this.jsonMapper.readValue(notification.payload(), PushMessage.class);
		long owner = this.studyProfiles.of(notification.studyId()).ownerUserId();
		return this.outbox.addWebPush(new NewWebPush(NotificationType.DAILY_RESULT, notification.studyId(), owner,
				studyDay, NotificationOutbox.dailyResultOwnerKey(notification.studyId(), studyDay), message,
				notification.expiresAt()));
	}

}
