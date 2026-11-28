package com.duskafka.studybot.notification.dispatch;

import com.duskafka.studybot.notification.outbox.NotificationChannel;
import com.duskafka.studybot.notification.outbox.NotificationStatus;
import com.duskafka.studybot.notification.outbox.NotificationType;
import com.duskafka.studybot.notification.persistence.NotificationQueue;
import com.duskafka.studybot.notification.persistence.NotificationQueue.ClaimedNotification;
import com.duskafka.studybot.notification.persistence.NotificationRepository;
import com.duskafka.studybot.progress.ReminderTargets;
import com.duskafka.studybot.study.StudyAccess;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 알림 발송기 (S13 만들 것 3, 5.8, pseudocode '발송'). 스케줄은 {@link DispatchSchedule}이 건다 (5초마다).
 *
 * <ol>
 * <li>잠금이 만료된 발송 중 행을 발송 대기로 되돌린다 (발송 중에 서버가 죽은 경우).</li>
 * <li>보낼 시각이 된 발송 대기 행을 {@value #BATCH_SIZE}개까지 {@code FOR UPDATE SKIP LOCKED}로 가져와 발송 중(잠금 만료 시각)으로 먼저
 * 커밋한다 ({@link NotificationQueue#claim}). 서버가 여러 대여도 같은 행을 둘이 가져가지 않는다. 그래서 ShedLock을 쓰지 않는다.</li>
 * <li>행마다 트랜잭션 밖에서: 만료됐으면 EXPIRED, 마감 전 알림인데 그새 풀었거나 면제권을 썼거나 탈퇴했으면 SKIPPED (T16), 규칙 변경 알림도
 * 받는 사람이 그새 탈퇴했으면 SKIPPED (사람이 정함 2026-11-27 '탈퇴한 멤버에게 보내지 않음'), 아니면 채널로 보낸다. 같은 확인을 공유 유도(방장을
 * 위임하고 탈퇴한 옛 방장)에도 거는 것은 에이전트 판단이다 (S13 PR 판단 기록).</li>
 * <li>결과: 받음 → SENT, 영구 실패 → FAILED, 일시 실패 → 재시도 시각을 정하고 PENDING (시도 횟수 +1).</li>
 * </ol>
 *
 * <p>'정확히 한 번'은 불가능하다 (5.8 '주의할 것'). 보낸 뒤 결과를 남기기 전에 죽으면 잠금 만료 뒤 다시 보낸다(적어도 한 번). 잠금 만료
 * 시각이 지난 행은 보내지 않고 남겨 둔다. 다른 발송기가 이미 다시 가져갔을 수 있어서다. 결과는 잠금 만료 시각이 같을 때만 남긴다
 * ({@link NotificationRepository} 머리말).
 *
 * <p>재시도 백오프: {@code Retry-After}가 있으면 그만큼(1초~1시간), 없으면 30초 × 2^(시도 횟수 − 1), 최대 30분이다. 따로 횟수 한도는 두지
 * 않는다. 만료 시각이 지나면 EXPIRED가 되기 때문이다 (S13 PR 판단 기록).
 *
 * <p>지표: {@code studybot.notification.dispatched}(결과별 수, {@code result} 태그: SENT·FAILED·SKIPPED·EXPIRED·RETRY, {@code reason}
 * 태그: 실패·재시도 까닭 또는 {@code none}). 발송 성공률은 SENT ÷ (SENT + FAILED)다 (metrics.md 10.2). 구독이 없는 사용자의 실패는
 * {@code reason=NO_SUBSCRIPTION}이라 따로 뺄 수 있다.
 */
@Slf4j
@Component
public class NotificationDispatcher {

	/** 한 번에 가져오는 행 수. 5초마다 돌아 1분에 240개까지 보낸다 (S13 PR 판단 기록). */
	static final int BATCH_SIZE = 20;

	/** 발송 중 잠금 시간 (pseudocode {@code lockedUntil = now + 2m}). */
	static final Duration LOCK_DURATION = Duration.ofMinutes(2);

	static final Duration BASE_BACKOFF = Duration.ofSeconds(30);

	static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

	private final NotificationQueue queue;

	private final NotificationRepository notifications;

	private final ReminderTargets reminderTargets;

	private final StudyAccess studyAccess;

	private final Map<NotificationChannel, NotificationSender> senders;

	private final MeterRegistry meterRegistry;

	private final Clock clock;

	NotificationDispatcher(NotificationQueue queue, NotificationRepository notifications,
			ReminderTargets reminderTargets, StudyAccess studyAccess, List<NotificationSender> senders,
			MeterRegistry meterRegistry, Clock clock) {
		this.queue = queue;
		this.notifications = notifications;
		this.reminderTargets = reminderTargets;
		this.studyAccess = studyAccess;
		this.senders = new EnumMap<>(NotificationChannel.class);
		for (NotificationSender sender : senders) {
			if (this.senders.put(sender.channel(), sender) != null) {
				throw new IllegalStateException("채널 하나에 발송기가 둘이에요: " + sender.channel());
			}
		}
		this.meterRegistry = meterRegistry;
		this.clock = clock;
	}

	/**
	 * 한 번 돈다.
	 *
	 * @return 가져간 행 수
	 */
	public int run() {
		Instant now = this.clock.instant();
		int released = this.notifications.releaseExpiredLocks(now);
		if (released > 0) {
			log.warn("잠금이 만료된 발송 중 알림 {}개를 발송 대기로 되돌렸어요", released);
		}
		// 잠금 만료 시각이 이 발송기의 소유 표시다. 메모리의 토큰이 DB에 저장된 값과 같도록 DB 정밀도(마이크로초)로 자른다. PostgreSQL JDBC
		// 드라이버도 쓸 때와 비교할 때 같은 방식으로 마이크로초에 맞추므로 자르지 않아도 지금은 같아지지만, 드라이버 동작에 기대지 않으려는 것이다.
		Instant lockedUntil = now.plus(LOCK_DURATION).truncatedTo(ChronoUnit.MICROS);
		List<ClaimedNotification> claimed = this.queue.claim(now, lockedUntil, BATCH_SIZE);
		for (ClaimedNotification notification : claimed) {
			try {
				process(notification);
			}
			catch (RuntimeException ex) {
				// 결과를 남기지 못했다. 잠금이 만료되면 다시 가져간다.
				log.error("알림 결과를 남기지 못했어요: notification={}", notification.id(), ex);
			}
		}
		return claimed.size();
	}

	private void process(ClaimedNotification notification) {
		Instant now = this.clock.instant();
		if (!now.isBefore(notification.token())) {
			log.warn("잠금이 만료돼 보내지 않고 남겨 둬요: notification={}", notification.id());
			return;
		}
		if (!now.isBefore(notification.expiresAt())) {
			finish(notification, NotificationStatus.EXPIRED, 0, null, now);
			return;
		}
		SendResult result;
		try {
			if (!stillWanted(notification)) {
				finish(notification, NotificationStatus.SKIPPED, 0, null, now);
				return;
			}
			result = send(notification, now);
		}
		catch (RuntimeException ex) {
			log.error("알림을 보내다 실패했어요: notification={}", notification.id(), ex);
			result = new SendResult.Retry(null, "ERROR_" + ex.getClass().getSimpleName());
		}
		switch (result) {
			case SendResult.Delivered delivered -> finish(notification, NotificationStatus.SENT, 1, null, now);
			case SendResult.Failed failed -> finish(notification, NotificationStatus.FAILED, 1, failed.reason(), now);
			case SendResult.Retry retry -> retryLater(notification, retry, now);
		}
	}

	/**
	 * 보내기 직전에 아직 보낼 까닭이 있는지 다시 확인한다 (5.8 '발송 직전 다시 확인'). 마감 전 알림은 그날 아직 '남음'인지(T16, 탈퇴 포함),
	 * 사람에게 가는 그 밖의 알림은 받는 사람이 아직 멤버인지 본다. 규칙 알림은 같은 사람에게 더 새 규칙 알림이 있으면 옛 정보라 보내지 않는다
	 * (예: 변경 알림이 재시도로 밀린 사이 취소됨. 에이전트 판단, S13 PR 판단 기록). 받는 사람이 없는 알림(스터디 채널, S14)은 그대로 보낸다.
	 */
	private boolean stillWanted(ClaimedNotification notification) {
		Long userId = notification.userId();
		if (NotificationType.REMINDER.name().equals(notification.type())) {
			return userId != null && notification.studyDay() != null
					&& this.reminderTargets.stillRemaining(notification.studyId(), userId, notification.studyDay());
		}
		if (userId == null) {
			return true;
		}
		if (NotificationType.RULE_CHANGED.name().equals(notification.type())
				&& this.notifications.existsNewerRuleNotice(notification.studyId(), userId, notification.id())) {
			return false;
		}
		return this.studyAccess.isActiveMember(notification.studyId(), userId);
	}

	private SendResult send(ClaimedNotification notification, Instant now) {
		NotificationSender sender = this.senders.get(NotificationChannel.valueOf(notification.channel()));
		if (sender == null) {
			return new SendResult.Failed("NO_SENDER");
		}
		return sender.send(notification, now);
	}

	private void finish(ClaimedNotification notification, NotificationStatus status, int attempted,
			@Nullable String lastError, Instant now) {
		if (this.notifications.finish(notification.id(), notification.token(), status.name(), attempted, lastError,
				now) == 0) {
			log.warn("다른 발송기가 가져가 결과를 남기지 않았어요: notification={}", notification.id());
			return;
		}
		count(status.name(), lastError);
	}

	private void retryLater(ClaimedNotification notification, SendResult.Retry retry, Instant now) {
		Duration delay = retry.retryAfter() != null ? retry.retryAfter() : backoff(notification.attempts() + 1);
		if (this.notifications.retryLater(notification.id(), notification.token(), now.plus(delay), retry.reason()) == 0) {
			log.warn("다른 발송기가 가져가 재시도 시각을 남기지 않았어요: notification={}", notification.id());
			return;
		}
		count("RETRY", retry.reason());
	}

	/** {@code attempt}번째 시도가 실패한 뒤 기다릴 시간: 30초 × 2^(attempt − 1), 최대 30분. */
	static Duration backoff(int attempt) {
		int exponent = Math.min(Math.max(attempt - 1, 0), 10);
		Duration delay = BASE_BACKOFF.multipliedBy(1L << exponent);
		return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
	}

	/** 결과와 실패 까닭(없으면 {@code none})을 태그로 센다. 태그 키는 늘 같다. */
	private void count(String result, @Nullable String reason) {
		this.meterRegistry
			.counter("studybot.notification.dispatched", "result", result, "reason", reason == null ? "none" : reason)
			.increment();
	}

}
