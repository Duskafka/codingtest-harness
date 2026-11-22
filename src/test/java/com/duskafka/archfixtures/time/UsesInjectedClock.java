package com.duskafka.archfixtures.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.IsoChronology;
import java.time.chrono.JapaneseDate;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * 통과해야 하는 예.
 * 주입된 Clock을 쓰는 now(Clock)·dateNow(Clock)·Clock.tick(Clock, Duration), 경과 시간 측정용 System.nanoTime(),
 * 시각을 직접 넘기는 new Date(long)·new GregorianCalendar(년, 월, 일).
 */
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

	public JapaneseDate japaneseToday() {
		return JapaneseDate.now(clock);
	}

	public ChronoLocalDate isoToday() {
		return IsoChronology.INSTANCE.dateNow(clock);
	}

	public Clock perSecond() {
		return Clock.tick(clock, Duration.ofSeconds(1));
	}

	public long elapsedNanos() {
		return System.nanoTime();
	}

	public Date dateOf(Instant instant) {
		return new Date(instant.toEpochMilli());
	}

	public GregorianCalendar newYear() {
		return new GregorianCalendar(2026, 0, 1);
	}

}
