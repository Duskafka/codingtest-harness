package com.duskafka.studybot.notification.reminder;

import com.duskafka.studybot.notification.card.Cards;
import com.duskafka.studybot.notification.link.PublicLinks;
import com.duskafka.studybot.notification.outbox.NotificationOutbox;
import com.duskafka.studybot.notification.outbox.NotificationOutbox.NewDiscordPost;
import com.duskafka.studybot.notification.outbox.NotificationOutbox.NewWebPush;
import com.duskafka.studybot.notification.outbox.NotificationType;
import com.duskafka.studybot.notification.outbox.PushMessage;
import com.duskafka.studybot.notification.persistence.StudyChannelRepository;
import com.duskafka.studybot.progress.ReminderTargets;
import com.duskafka.studybot.progress.ReminderTargets.ReminderDay;
import com.duskafka.studybot.progress.ReminderTargets.Remaining;
import com.duskafka.studybot.study.ReminderDays;
import com.duskafka.studybot.study.ReminderDays.DueReminder;
import com.duskafka.studybot.study.StudyMode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스터디 하나의 마감 전 알림을 한 트랜잭션으로 만든다 (pseudocode '마감 전 알림'의 {@code transaction:}). 알림 행과 진행 지점
 * ({@code last_reminded_day})이 함께 커밋되거나 함께 롤백된다.
 */
@Component
@RequiredArgsConstructor
class ReminderWriter {

	private final ReminderDays reminderDays;

	private final ReminderTargets reminderTargets;

	private final NotificationOutbox outbox;

	private final StudyChannelRepository studyChannels;

	/**
	 * 알림 시각이 지났고 아직 만들지 않은 날이면, 대상마다 {@code REMINDER}, 대상이 있으면 방장에게 {@code SHARE_PROMPT}를 넣고 진행 지점을 그날로
	 * 올린다. 만료는 그날 마감이다 (5.8). 혼자 쓰기 스터디는 공유가 없어 {@code SHARE_PROMPT}를 만들지 않는다 (R15).
	 *
	 * <p>대상이 있고 디스코드가 켜진 스터디면 같은 트랜잭션에서 {@code DISCORD_STATUS}도 넣는다 (S14 만들 것 3, pseudocode '마감 전 알림'의
	 * {@code if study.discordEnabled}). 임베드(멤버 이름)는 발송기가 보낼 때 만든다. 혼자 쓰기 스터디는 디스코드를 등록할 수 없다.
	 *
	 * @return 새로 넣은 알림 수
	 */
	@Transactional
	public int remind(long studyId) {
		Optional<DueReminder> found = this.reminderDays.due(studyId);
		if (found.isEmpty()) {
			return 0;
		}
		DueReminder due = found.get();
		ReminderDay day = this.reminderTargets.remainingOn(studyId, due.studyDay());
		int created = 0;
		for (Remaining member : day.remaining()) {
			boolean added = this.outbox.addWebPush(new NewWebPush(NotificationType.REMINDER, studyId, member.userId(),
					day.studyDay(), NotificationOutbox.reminderKey(studyId, member.userId(), day.studyDay()),
					ReminderMessages.reminder(studyId, due.studyName(), member.problemsLeft(), day.deadline(), due.zoneId()),
					day.deadline()));
			created += added ? 1 : 0;
		}
		Long ownerUserId = day.ownerUserId();
		if (!day.remaining().isEmpty() && ownerUserId != null && due.mode() != StudyMode.SOLO) {
			boolean added = this.outbox.addWebPush(new NewWebPush(NotificationType.SHARE_PROMPT, studyId, ownerUserId,
					day.studyDay(), NotificationOutbox.sharePromptKey(studyId, day.studyDay()),
					ReminderMessages.sharePrompt(studyId, due.studyName(), day.remaining().size()), day.deadline()));
			created += added ? 1 : 0;
		}
		if (!day.remaining().isEmpty() && this.studyChannels.isPostable(studyId)) {
			boolean added = this.outbox.addDiscord(new NewDiscordPost(NotificationType.DISCORD_STATUS, studyId,
					day.studyDay(), NotificationOutbox.discordStatusKey(studyId, day.studyDay()),
					new PushMessage(NotificationType.DISCORD_STATUS.name(), due.studyName(),
							Cards.dateText(day.studyDay()) + " 인증 현황", PublicLinks.todayPath(studyId)),
					day.deadline()));
			created += added ? 1 : 0;
		}
		this.reminderDays.markReminded(studyId, day.studyDay());
		return created;
	}

}
