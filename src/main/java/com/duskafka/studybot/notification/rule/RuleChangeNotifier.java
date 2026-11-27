package com.duskafka.studybot.notification.rule;

import com.duskafka.studybot.notification.outbox.NotificationOutbox;
import com.duskafka.studybot.notification.outbox.NotificationOutbox.NewWebPush;
import com.duskafka.studybot.notification.outbox.NotificationType;
import com.duskafka.studybot.notification.outbox.PushMessage;
import com.duskafka.studybot.study.StudyRulesChanged;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 규칙이 바뀌면 멤버에게 {@code RULE_CHANGED}를 만든다 (S13 만들 것 4, R2, 5.8 '알림 종류').
 *
 * <p>study → notification 화살표가 없어서(architecture.md 4.2, 알려진 충돌 5번) study가 발행한 {@link StudyRulesChanged}를 받는다. 받는
 * 시점은 규칙 변경 트랜잭션 안이고(동기 {@link EventListener}), 알림 행도 그 트랜잭션에 넣는다 (사람이 정함, 2026-11-27 S13 세션 "Listener
 * in the same transaction (Recommended)"). 그래서 이벤트가 사라질 틈이 없고 규칙 변경과 알림이 함께 커밋되거나 함께 롤백된다. 4.2의 '커밋
 * 뒤 이벤트' 문구와는 다르다 (S13 PR 스펙 변경 제안). 외부 발송은 여기서 하지 않는다. 발송기가 커밋된 행만 본다.
 *
 * <p>만료는 만든 뒤 24시간이다. 날과 상관없는 알림이라 그 안에 보내지 못하면 의미가 줄어든다고 봤다 (S13 PR 판단 기록).
 */
@Component
@RequiredArgsConstructor
public class RuleChangeNotifier {

	/** 규칙 변경 알림을 보낼 수 있는 시간. */
	static final Duration TIME_TO_LIVE = Duration.ofHours(24);

	private final NotificationOutbox outbox;

	private final Clock clock;

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void on(StudyRulesChanged event) {
		Instant expiresAt = this.clock.instant().plus(TIME_TO_LIVE);
		PushMessage message = new PushMessage(NotificationType.RULE_CHANGED.name(), event.studyName(),
				"규칙이 바뀌어요. " + event.effectiveFrom().getMonthValue() + "월 " + event.effectiveFrom().getDayOfMonth()
						+ "일부터 적용돼요",
				"/studies/" + event.studyId());
		for (long userId : event.recipientUserIds()) {
			this.outbox.addWebPush(new NewWebPush(NotificationType.RULE_CHANGED, event.studyId(), userId, null,
					NotificationOutbox.ruleChangedKey(event.studyId(), event.latestRuleId(), userId), message, expiresAt));
		}
	}

}
