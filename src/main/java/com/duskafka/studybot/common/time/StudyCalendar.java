package com.duskafka.studybot.common.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** S02 테스트를 먼저 쓰기 위한 빈 껍데기. */
public final class StudyCalendar {

	public static final Duration JUDGE_MARGIN = Duration.ofMinutes(10);

	private StudyCalendar() {
	}

	public static StudyCalendar of(StudyTimeRule rule) {
		return new StudyCalendar();
	}

	public LocalDate studyDayOf(Instant instant) {
		throw new UnsupportedOperationException("S02");
	}

	public Instant boundaryOf(LocalDate studyDay) {
		throw new UnsupportedOperationException("S02");
	}

	public boolean isInGrace(Instant instant) {
		throw new UnsupportedOperationException("S02");
	}

	public Instant reminderAt(LocalDate studyDay) {
		throw new UnsupportedOperationException("S02");
	}

	public Instant judgeAt(LocalDate studyDay) {
		throw new UnsupportedOperationException("S02");
	}

	public boolean isRestDay(LocalDate studyDay) {
		throw new UnsupportedOperationException("S02");
	}

}
