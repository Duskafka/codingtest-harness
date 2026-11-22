package com.duskafka.studybot.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockConfigurationTest {

	@Test
	@DisplayName("S01: 앱의 Clock은 UTC 시스템 시계다 (D7: 저장은 UTC)")
	void clockIsUtcSystemClock() {
		Clock clock = new ClockConfiguration().clock();

		assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
		assertThat(clock).isEqualTo(Clock.systemUTC());
	}

}
