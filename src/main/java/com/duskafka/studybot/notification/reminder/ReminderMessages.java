package com.duskafka.studybot.notification.reminder;

import com.duskafka.studybot.notification.outbox.NotificationType;
import com.duskafka.studybot.notification.outbox.PushMessage;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 마감 전 알림과 공유 유도의 문구 (5.8 '주의할 것': "오늘 1문제 남았어요, 24시 마감"처럼 작게, 개인정보 없이). 누르면 오늘 현황을 연다
 * (5.8 '서비스 워커: 알림을 누르면 오늘 현황 열기'). 화면 경로는 S16이 정하기 전이라 api.md 경로와 같은 모양으로 둔다 (S13 PR 판단 기록).
 */
final class ReminderMessages {

	private ReminderMessages() {
	}

	/** 아직 못 푼 멤버에게. */
	static PushMessage reminder(long studyId, String studyName, int problemsLeft, Instant deadline, ZoneId zoneId) {
		return new PushMessage(NotificationType.REMINDER.name(), studyName,
				"오늘 " + problemsLeft + "문제 남았어요, " + deadlineText(deadline, zoneId) + " 마감", todayUrl(studyId));
	}

	/** 방장에게: 못 푼 멤버가 있으니 현황을 공유해 보라고. */
	static PushMessage sharePrompt(long studyId, String studyName, int remainingMembers) {
		return new PushMessage(NotificationType.SHARE_PROMPT.name(), studyName,
				"아직 못 푼 멤버가 " + remainingMembers + "명 있어요. 오늘 현황을 공유해 보세요", todayUrl(studyId));
	}

	private static String todayUrl(long studyId) {
		return "/studies/" + studyId + "/today";
	}

	/**
	 * 마감 시각을 스터디 시간대로 적는다. 기준 시각은 00:00~06:00이라(R1) 자정은 '24시', 그 밖은 '새벽 N시(M분)'다.
	 */
	static String deadlineText(Instant deadline, ZoneId zoneId) {
		LocalTime time = deadline.atZone(zoneId).toLocalTime();
		if (time.getHour() == 0 && time.getMinute() == 0) {
			return "24시";
		}
		String hour = (time.getHour() == 0 ? "밤 12" : "새벽 " + time.getHour()) + "시";
		return time.getMinute() == 0 ? hour : hour + " " + time.getMinute() + "분";
	}

}
