package com.duskafka.studybot;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 앱의 {@link java.time.Clock} 빈 대신 {@link MutableClock}을 주입한다 (@Primary). */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock();
	}

}
