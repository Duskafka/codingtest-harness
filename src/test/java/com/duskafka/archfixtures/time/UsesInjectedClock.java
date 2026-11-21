package com.duskafka.archfixtures.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

/** 주입된 Clock을 쓰는 now(Clock), 경과 시간 측정용 System.nanoTime(), 시각을 넘기는 new Date(long)은 통과한다. */
public class UsesInjectedClock {

	private final Clock clock;

	public UsesInjectedClock(Clock clock) {
		this.clock = clock;
	}

	public LocalDate today() {
		return LocalDate.now(clock);
	}

	public Instant instant() {
		return Instant.now(clock);
	}

	public long elapsedNanos() {
		return System.nanoTime();
	}

	public Date dateOf(Instant instant) {
		return new Date(instant.toEpochMilli());
	}

}
