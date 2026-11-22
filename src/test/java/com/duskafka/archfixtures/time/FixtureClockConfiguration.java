package com.duskafka.archfixtures.time;

import java.time.Clock;
import java.time.Instant;

/** Clock 설정 클래스 역할: 예외로 허용된다. */
public class FixtureClockConfiguration {

	public Clock clock() {
		return Clock.systemUTC();
	}

	public Instant startedAt() {
		return Instant.now();
	}

}
