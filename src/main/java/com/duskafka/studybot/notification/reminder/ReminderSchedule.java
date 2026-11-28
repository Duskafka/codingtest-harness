package com.duskafka.studybot.notification.reminder;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 마감 전 알림 생성의 스케줄 진입점 (S13 만들 것 2, 5.8 '스케줄러 (1분마다, ShedLock)').
 *
 * <ul>
 * <li>주기는 {@code studybot.notification.reminder.cron}(기본 1분마다)이다. 테스트는 {@code "-"}로 끄고 {@link ReminderGenerator}를
 * 직접 부른다.</li>
 * <li>서버가 여러 대여도 한 번만 돈다 (ShedLock, {@code common.scheduling.SchedulingConfiguration}). 최대 잠금 10분은 한 번이 걸릴
 * 시간보다 길게 잡았다. 최소 잠금 30초는 서버 시계가 조금 달라 같은 분에 두 번 도는 것을 막는다. 두 번 돌아도 알림은 하나다 (T15).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ReminderSchedule {

	/** ShedLock 잠금 이름. */
	static final String LOCK_NAME = "deadline-reminder";

	private final ReminderGenerator generator;

	@Scheduled(cron = "${studybot.notification.reminder.cron:0 * * * * *}")
	@SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
	public void runScheduled() {
		this.generator.run();
	}

}
