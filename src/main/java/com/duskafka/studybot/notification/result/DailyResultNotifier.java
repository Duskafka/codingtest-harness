package com.duskafka.studybot.notification.result;

import com.duskafka.studybot.notification.card.Cards;
import com.duskafka.studybot.notification.card.Cards.ResultCounts;
import com.duskafka.studybot.notification.link.PublicLinks;
import com.duskafka.studybot.notification.outbox.NotificationOutbox;
import com.duskafka.studybot.notification.outbox.NotificationOutbox.NewDiscordPost;
import com.duskafka.studybot.notification.outbox.NotificationOutbox.NewWebPush;
import com.duskafka.studybot.notification.outbox.NotificationType;
import com.duskafka.studybot.notification.outbox.PushMessage;
import com.duskafka.studybot.notification.persistence.StudyChannelRepository;
import com.duskafka.studybot.progress.StudyDayBoards;
import com.duskafka.studybot.study.StudyDayJudged;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판정이 끝난 날의 결과 알림 {@code DAILY_RESULT}를 만든다 (S14 만들 것 5, 5.8 표 '판정 직후', 결정 제안서 010).
 *
 * <p>judgment → notification 화살표가 없어서(알려진 충돌 2번) study가 진행 지점을 올리는 트랜잭션에서 발행한 {@link StudyDayJudged}를 받는다.
 * 받는 시점은 그 트랜잭션 안이고(동기 {@link EventListener}), 알림 행도 그 트랜잭션에 넣는다 (사람이 정함, 2026-11-28 "같은 트랜잭션 리스너").
 * 그래서 진행 지점이 올라갔다면 알림 행도 있고, 여기서 실패하면 진행 지점도 올라가지 않아 다음 판정 배치가 다시 한다. 하루에 하나는
 * {@code DAILY_RESULT:{study}:{day}} UNIQUE가 마지막으로 막는다.
 *
 * <ul>
 * <li>채널: 디스코드가 켜져 있으면 디스코드 채널 행, 아니면 방장 웹 푸시다 (5.8 표 '채널 또는 방장'). 혼자 쓰기 스터디는 디스코드가 없어 방장 웹
 * 푸시다.</li>
 * <li>성공·실패·면제권 결과가 하나도 없는 날(쉬는 날, 판정 대상이 없는 날)은 만들지 않는다.</li>
 * <li>만료는 판정한 날 다음 스터디 일이 끝나는 시각이다. 오래 꺼졌다 따라잡는 날처럼 이미 지났으면 만들지 않는다 (5.8 '마감이 지난 날은 만들지
 * 않아요'와 같은 생각, S14 PR 판단 기록).</li>
 * <li>payload는 숫자만이다 (이름 없음, 5.8 '개인정보는 최소로'). 디스코드 임베드의 실패한 멤버 이름은 발송기가 보낼 때 DB에서 만든다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DailyResultNotifier {

	private final StudyDayBoards boards;

	private final StudyChannelRepository studyChannels;

	private final NotificationOutbox outbox;

	private final Clock clock;

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void on(StudyDayJudged event) {
		if (!this.clock.instant().isBefore(event.nextDayEndsAt())) {
			return;
		}
		ResultCounts counts = Cards.resultCounts(this.boards.resultCountsOf(event.studyId(), event.studyDay()));
		if (!counts.hasDecisions()) {
			return;
		}
		String key = NotificationOutbox.dailyResultKey(event.studyId(), event.studyDay());
		PushMessage message = new PushMessage(NotificationType.DAILY_RESULT.name(), event.studyName(),
				Cards.resultPushBody(event.studyDay(), counts), PublicLinks.dayPath(event.studyId(), event.studyDay()));
		if (this.studyChannels.isPostable(event.studyId())) {
			this.outbox.addDiscord(new NewDiscordPost(NotificationType.DAILY_RESULT, event.studyId(), event.studyDay(), key,
					message, event.nextDayEndsAt()));
			return;
		}
		this.outbox.addWebPush(new NewWebPush(NotificationType.DAILY_RESULT, event.studyId(), event.ownerUserId(),
				event.studyDay(), key, message, event.nextDayEndsAt()));
	}

}
