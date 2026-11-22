package com.duskafka.studybot.common.time;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/** S02 테스트를 먼저 쓰기 위한 빈 껍데기. */
public record StudyTimeRule(ZoneId zoneId, LocalTime dayBoundary, int graceMinutes, int reminderOffsetMinutes,
		Set<DayOfWeek> restDays) {

	public Duration grace() {
		throw new UnsupportedOperationException("S02");
	}

	public Duration reminderOffset() {
		throw new UnsupportedOperationException("S02");
	}

}
