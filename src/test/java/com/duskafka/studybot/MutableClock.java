package com.duskafka.studybot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트용 시계. 통합 테스트가 현재 시각을 정하고({@link #setInstant}) 옮길({@link #advance}) 수 있다.
 *
 * <p>{@link IntegrationTest}를 붙인 테스트에서는 이 시계가 앱의 {@link Clock} 빈 자리에 들어가고(@Primary),
 * 테스트 메서드마다 {@link #DEFAULT_INSTANT}로 돌아간다 ({@link MutableClockResetListener}).
 * 여러 스레드에서 읽어도 안전하다. {@link #withZone}으로 만든 시계는 같은 시각을 공유한다.
 */
public final class MutableClock extends Clock {

	/** 테스트가 시각을 정하지 않았을 때의 시각. 테스트는 필요한 시각을 직접 정한다. */
	public static final Instant DEFAULT_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

	private final AtomicReference<Instant> instant;

	private final ZoneId zone;

	public MutableClock() {
		this(new AtomicReference<>(DEFAULT_INSTANT), ZoneOffset.UTC);
	}

	private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
		this.instant = instant;
		this.zone = zone;
	}

	public void setInstant(Instant newInstant) {
		instant.set(Objects.requireNonNull(newInstant, "newInstant"));
	}

	public void advance(Duration duration) {
		Objects.requireNonNull(duration, "duration");
		instant.updateAndGet(current -> current.plus(duration));
	}

	public void reset() {
		instant.set(DEFAULT_INSTANT);
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId newZone) {
		return new MutableClock(instant, Objects.requireNonNull(newZone, "newZone"));
	}

	@Override
	public Instant instant() {
		return instant.get();
	}

}
