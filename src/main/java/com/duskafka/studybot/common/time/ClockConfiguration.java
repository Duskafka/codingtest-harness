package com.duskafka.studybot.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 앱 전체가 주입받는 {@link Clock}. 시간은 이 빈으로만 다룬다 (CLAUDE.md).
 *
 * <p>시스템 시계는 여기서만 만든다. 다른 main 코드에서 {@code now()} 등으로 현재 시각을 직접 읽으면
 * ArchUnit 테스트({@code TimeRules})가 실패한다.
 * 시간대는 UTC다. 저장은 UTC로 하고, 판정은 스터디 시간대로 따로 계산한다 (D7).
 * 스터디 시간대 계산은 {@code StudyCalendar}(S02)가 맡는다.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
