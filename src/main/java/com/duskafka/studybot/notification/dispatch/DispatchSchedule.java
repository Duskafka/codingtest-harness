package com.duskafka.studybot.notification.dispatch;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 발송기의 스케줄 진입점 (S13 만들 것 3 '발송기 (5초마다)'). 주기는 {@code studybot.notification.dispatch.cron}(기본 5초마다)이다.
 * 테스트는 {@code "-"}로 끄고 {@link NotificationDispatcher}를 직접 부른다.
 *
 * <p>ShedLock을 쓰지 않는다. 서버가 여러 대여도 {@code FOR UPDATE SKIP LOCKED}로 서로 다른 행을 가져간다 (5.8). 같은 서버 안에서는 앞 실행이
 * 끝난 뒤 다음 실행이 돈다(cron 작업은 겹쳐 돌지 않음).
 */
@Component
@RequiredArgsConstructor
public class DispatchSchedule {

	private final NotificationDispatcher dispatcher;

	@Scheduled(cron = "${studybot.notification.dispatch.cron:*/5 * * * * *}")
	public void runScheduled() {
		this.dispatcher.run();
	}

}
