package com.duskafka.studybot.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** S05에서 더한 계산: 다음 스터디 일(R14)과 기수의 마지막 날(R13, 5.13). */
class StudyCalendarDayAndSeasonTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Test
	@DisplayName("S05 (R14): 기준 00:00 스터디에서 11/20 15:00 KST의 다음 스터디 일은 11/21")
	void nextStudyDayAtMidnightBoundary() {
		StudyCalendar calendar = calendar(LocalTime.MIDNIGHT);

		assertThat(calendar.nextStudyDayOf(Instant.parse("2026-11-20T06:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 21));
	}

	@Test
	@DisplayName("S05 (R14): 기준 06:00 스터디에서 11/21 03:00 KST는 아직 11/20이라 다음 스터디 일은 11/21")
	void nextStudyDayReflectsDayBoundary() {
		StudyCalendar calendar = calendar(LocalTime.of(6, 0));

		assertThat(calendar.nextStudyDayOf(Instant.parse("2026-11-20T18:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 21));
		assertThat(calendar(LocalTime.MIDNIGHT).nextStudyDayOf(Instant.parse("2026-11-20T18:00:00Z")))
			.isEqualTo(LocalDate.of(2026, 11, 22));
	}

	@Test
	@DisplayName("S05 (R13): 기수의 마지막 날은 시작일부터 기수 길이만큼 센 날 (28일 → +27일, 7일 → +6일, 1일 → 시작일)")
	void seasonEndDayCountsLengthFromStart() {
		LocalDate start = LocalDate.of(2026, 11, 21);

		assertThat(StudyCalendar.seasonEndDay(start, 28)).isEqualTo(LocalDate.of(2026, 12, 18));
		assertThat(StudyCalendar.seasonEndDay(start, 7)).isEqualTo(LocalDate.of(2026, 11, 27));
		assertThat(StudyCalendar.seasonEndDay(start, 1)).isEqualTo(start);
	}

	@Test
	@DisplayName("S05: 기수 길이가 1일보다 짧으면 계산하지 않는다")
	void seasonEndDayRejectsNonPositiveLength() {
		assertThatThrownBy(() -> StudyCalendar.seasonEndDay(LocalDate.of(2026, 11, 21), 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static StudyCalendar calendar(LocalTime dayBoundary) {
		return StudyCalendar.of(new StudyTimeRule(SEOUL, dayBoundary, 30, 120, Set.of()));
	}

}
