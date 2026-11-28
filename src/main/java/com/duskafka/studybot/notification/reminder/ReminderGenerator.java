package com.duskafka.studybot.notification.reminder;

import com.duskafka.studybot.study.ReminderDays;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 마감 전 알림 생성 (S13 만들 것 2, 5.8, pseudocode '마감 전 알림'). 스케줄은 {@link ReminderSchedule}이 건다 (1분마다, ShedLock).
 *
 * <ul>
 * <li>스터디마다 트랜잭션 하나로 만든다 ({@link ReminderWriter}). 한 스터디의 오류는 다른 스터디를 막지 않는다 (판정 배치와 같은 방식).</li>
 * <li>알림 시각이 지났는데 아직 만들지 않은 (스터디, 날)만 만든다. 날은 지금 시각이 속한 스터디 일이라 마감이 지난 날은 만들지 않는다. 진행
 * 지점은 {@code studies.last_reminded_day}이다.</li>
 * <li>두 번 돌아도 알림은 하나다: 진행 지점이 그날이면 건너뛰고, 진행 지점이 없어져도 {@code dedupe_key} UNIQUE가 막는다 (T15).</li>
 * </ul>
 *
 * <p>지표: {@code studybot.notification.reminder.errors}(스터디마다 생성에 실패한 수). 만든 알림 수는
 * {@code studybot.notification.created}다 ({@code NotificationOutbox}).
 */
@Slf4j
@Component
public class ReminderGenerator {

	private final ReminderDays reminderDays;

	private final ReminderWriter writer;

	private final Counter errorCounter;

	ReminderGenerator(ReminderDays reminderDays, ReminderWriter writer, MeterRegistry meterRegistry) {
		this.reminderDays = reminderDays;
		this.writer = writer;
		this.errorCounter = Counter.builder("studybot.notification.reminder.errors")
			.description("마감 전 알림 생성에 실패한 스터디 수")
			.register(meterRegistry);
	}

	/**
	 * 활성 스터디마다 알림을 만든다.
	 *
	 * @return 새로 넣은 알림 수
	 */
	public int run() {
		int created = 0;
		for (long studyId : this.reminderDays.activeStudyIds()) {
			try {
				created += remindStudy(studyId);
			}
			catch (RuntimeException ex) {
				log.error("마감 전 알림을 만들지 못했어요: study={}", studyId, ex);
				this.errorCounter.increment();
			}
		}
		if (created > 0) {
			log.info("마감 전 알림: {}개 만듦", created);
		}
		return created;
	}

	/**
	 * 스터디 하나의 알림을 만든다.
	 *
	 * @return 새로 넣은 알림 수
	 */
	public int remindStudy(long studyId) {
		return this.writer.remind(studyId);
	}

}
