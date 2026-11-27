package com.duskafka.studybot.judgment;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 판정 배치의 스케줄 진입점 (S12 만들 것 1·2, 5.10 '5분마다 실행 (ShedLock)').
 *
 * <ul>
 * <li>주기는 {@code studybot.judgment.cron}(기본 5분마다)이다. 테스트는 {@code "-"}로 끄고 {@link DailyJudgmentBatch}를 직접 부른다.</li>
 * <li>서버가 여러 대여도 한 번만 돈다 (ShedLock, {@code common.scheduling.SchedulingConfiguration}). 최대 잠금 30분은 배치 한 번이
 * 걸릴 시간보다 길게 잡았다 (5.10 '락 유지 시간 > 예상 실행 시간'). 최소 잠금 1분은 서버 둘의 시계가 조금 달라 짧은 실행이 연달아 두 번 도는
 * 것을 막는다. 판정은 멱등이라 두 번 돌아도 결과는 같다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JudgmentSchedule {

	/** ShedLock 잠금 이름. */
	static final String LOCK_NAME = "daily-judgment";

	private final DailyJudgmentBatch batch;

	@Scheduled(cron = "${studybot.judgment.cron:0 */5 * * * *}")
	@SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
	public void runScheduled() {
		this.batch.run();
	}

}
