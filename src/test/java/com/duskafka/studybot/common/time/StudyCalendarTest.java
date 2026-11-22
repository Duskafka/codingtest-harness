package com.duskafka.studybot.common.time;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 시간 계산 (S02, T25의 계산 부분). 스터디 일은 {@code [D 기준 시각, D+1 기준 시각)}이고,
 * 유예는 {@code [기준 시각, 기준 시각 + 유예)}다.
 */
class StudyCalendarTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");

	private static final ZoneId LORD_HOWE = ZoneId.of("Australia/Lord_Howe");

	/** 수용 기준의 기본값: Asia/Seoul, 기준 시각 00:00, 유예 30분, 알림 120분 전. */
	private static final StudyCalendar SEOUL_DEFAULT = calendar(SEOUL, "00:00", 30);

	private static StudyCalendar calendar(ZoneId zone, String dayBoundary, int graceMinutes) {
		return calendar(zone, LocalTime.parse(dayBoundary), graceMinutes);
	}

	private static StudyCalendar calendar(ZoneId zone, LocalTime dayBoundary, int graceMinutes) {
		return StudyCalendar.of(new StudyTimeRule(zone, dayBoundary, graceMinutes, 120, Set.of()));
	}

	private static Instant at(String offsetDateTime) {
		return OffsetDateTime.parse(offsetDateTime).toInstant();
	}

	private static LocalDate day(String date) {
		return LocalDate.parse(date);
	}

	// 수용 기준 (Asia/Seoul, 기준 00:00, 유예 30분, 알림 120분 전)

	@Test
	@DisplayName("S02: 11/20 23:50 수신은 스터디 일 11/20")
	void beforeBoundaryBelongsToThatDay() {
		assertThat(SEOUL_DEFAULT.studyDayOf(at("2026-11-20T23:50+09:00"))).isEqualTo(day("2026-11-20"));
	}

	@Test
	@DisplayName("S02: 11/21 00:20 수신은 스터디 일 11/21이고 유예 안")
	void shortlyAfterBoundaryIsNextDayInGrace() {
		Instant receivedAt = at("2026-11-21T00:20+09:00");

		assertThat(SEOUL_DEFAULT.studyDayOf(receivedAt)).isEqualTo(day("2026-11-21"));
		assertThat(SEOUL_DEFAULT.isInGrace(receivedAt)).isTrue();
	}

	@Test
	@DisplayName("S02: 11/21 00:35 수신은 스터디 일 11/21이고 유예 밖")
	void afterGraceIsNextDayOutsideGrace() {
		Instant receivedAt = at("2026-11-21T00:35+09:00");

		assertThat(SEOUL_DEFAULT.studyDayOf(receivedAt)).isEqualTo(day("2026-11-21"));
		assertThat(SEOUL_DEFAULT.isInGrace(receivedAt)).isFalse();
	}

	@Test
	@DisplayName("T25: 기준 06:00 스터디에서 11/21 03:00 수신은 스터디 일 11/20")
	void dayBoundary0600CountsEarlyMorningAsPreviousDay() {
		StudyCalendar calendar = calendar(SEOUL, "06:00", 30);

		assertThat(calendar.studyDayOf(at("2026-11-21T03:00+09:00"))).isEqualTo(day("2026-11-20"));
	}

	@Test
	@DisplayName("S02: judgeAt(11/20)은 11/21 00:40+09:00 (D+1 기준 시각 + 유예 30분 + 여유 10분, R9)")
	void judgeAtIsNextBoundaryPlusGracePlusMargin() {
		assertThat(SEOUL_DEFAULT.judgeAt(day("2026-11-20"))).isEqualTo(at("2026-11-21T00:40+09:00"));
	}

	@Test
	@DisplayName("S02: reminderAt(11/20)은 11/20 22:00+09:00 (D+1 기준 시각 − 알림 오프셋 120분, 5.8)")
	void reminderAtIsNextBoundaryMinusOffset() {
		assertThat(SEOUL_DEFAULT.reminderAt(day("2026-11-20"))).isEqualTo(at("2026-11-20T22:00+09:00"));
	}

	@Test
	@DisplayName("S02: 쉬는 요일이 토·일이면 11/21(토)·11/22(일)은 쉬는 날이고 11/20(금)·11/23(월)은 아니다")
	void restDaysFollowConfiguredDaysOfWeek() {
		StudyCalendar calendar = StudyCalendar
			.of(new StudyTimeRule(SEOUL, LocalTime.MIDNIGHT, 30, 120, Set.of(SATURDAY, SUNDAY)));

		assertThat(calendar.isRestDay(day("2026-11-21"))).isTrue();
		assertThat(calendar.isRestDay(day("2026-11-22"))).isTrue();
		assertThat(calendar.isRestDay(day("2026-11-20"))).isFalse();
		assertThat(calendar.isRestDay(day("2026-11-23"))).isFalse();
	}

	@Test
	@DisplayName("S02: 쉬는 요일이 없으면 어느 요일도 쉬는 날이 아니다")
	void noRestDaysMeansEveryDayIsJudged() {
		for (LocalDate d = day("2026-11-16"); d.isBefore(day("2026-11-23")); d = d.plusDays(1)) {
			assertThat(SEOUL_DEFAULT.isRestDay(d)).as("%s(%s)", d, d.getDayOfWeek()).isFalse();
		}
	}

	// 경계값: 스터디 일 [D 기준 시각, D+1 기준 시각), 유예 [기준 시각, 기준 시각 + 유예)

	@Test
	@DisplayName("S02: boundaryOf(11/21)은 11/21 00:00+09:00")
	void boundaryOfIsDayBoundaryInStudyZone() {
		assertThat(SEOUL_DEFAULT.boundaryOf(day("2026-11-21"))).isEqualTo(at("2026-11-21T00:00+09:00"));
	}

	@Test
	@DisplayName("S02: 정확히 기준 시각에 받으면 그날이고 유예 안, 1나노초 전이면 전날이고 유예 밖")
	void boundaryInstantStartsTheDay() {
		Instant boundary = at("2026-11-21T00:00+09:00");
		Instant justBefore = boundary.minusNanos(1);

		assertThat(SEOUL_DEFAULT.studyDayOf(boundary)).isEqualTo(day("2026-11-21"));
		assertThat(SEOUL_DEFAULT.isInGrace(boundary)).isTrue();
		assertThat(SEOUL_DEFAULT.studyDayOf(justBefore)).isEqualTo(day("2026-11-20"));
		assertThat(SEOUL_DEFAULT.isInGrace(justBefore)).isFalse();
	}

	@Test
	@DisplayName("S02: 유예 끝 시각(기준 시각 + 30분) 정각은 유예 밖, 1나노초 전은 유예 안")
	void graceEndIsExclusive() {
		Instant graceEnd = at("2026-11-21T00:30+09:00");

		assertThat(SEOUL_DEFAULT.isInGrace(graceEnd)).isFalse();
		assertThat(SEOUL_DEFAULT.isInGrace(graceEnd.minusNanos(1))).isTrue();
	}

	@Test
	@DisplayName("S02: 유예가 0분이면 기준 시각 정각도 유예 밖이고, 판정은 기준 시각 + 10분")
	void zeroGraceIsNeverInGrace() {
		StudyCalendar calendar = calendar(SEOUL, "00:00", 0);

		assertThat(calendar.isInGrace(at("2026-11-21T00:00+09:00"))).isFalse();
		assertThat(calendar.judgeAt(day("2026-11-20"))).isEqualTo(at("2026-11-21T00:10+09:00"));
	}

	@Test
	@DisplayName("S02: 유예가 120분이면 기준 시각 + 120분 정각 직전까지 유예 안")
	void maximumGrace() {
		StudyCalendar calendar = calendar(SEOUL, "00:00", 120);
		Instant graceEnd = at("2026-11-21T02:00+09:00");

		assertThat(calendar.isInGrace(graceEnd.minusNanos(1))).isTrue();
		assertThat(calendar.isInGrace(graceEnd)).isFalse();
		assertThat(calendar.judgeAt(day("2026-11-20"))).isEqualTo(at("2026-11-21T02:10+09:00"));
	}

	@Test
	@DisplayName("S02: 시간 규칙 없이(null)는 만들 수 없다")
	void requiresRule() {
		assertThatNullPointerException().isThrownBy(() -> StudyCalendar.of(null));
	}

	// T25: 다른 기준 시각, 다른 시간대

	@Test
	@DisplayName("T25: 기준 06:00 스터디에서 11/21 06:00 정각은 11/21, 1나노초 전은 11/20")
	void dayBoundary0600Edges() {
		StudyCalendar calendar = calendar(SEOUL, "06:00", 30);
		Instant boundary = at("2026-11-21T06:00+09:00");

		assertThat(calendar.boundaryOf(day("2026-11-21"))).isEqualTo(boundary);
		assertThat(calendar.studyDayOf(boundary)).isEqualTo(day("2026-11-21"));
		assertThat(calendar.studyDayOf(boundary.minusNanos(1))).isEqualTo(day("2026-11-20"));
		assertThat(calendar.isInGrace(at("2026-11-21T06:29+09:00"))).isTrue();
		assertThat(calendar.isInGrace(at("2026-11-21T06:30+09:00"))).isFalse();
	}

	@Test
	@DisplayName("T25: 기준 06:00 스터디의 알림·판정 시각도 D+1 기준 시각에서 센다 (11/20 → 알림 11/21 04:00, 판정 11/21 06:40)")
	void dayBoundary0600ReminderAndJudgment() {
		StudyCalendar calendar = calendar(SEOUL, "06:00", 30);

		assertThat(calendar.reminderAt(day("2026-11-20"))).isEqualTo(at("2026-11-21T04:00+09:00"));
		assertThat(calendar.judgeAt(day("2026-11-20"))).isEqualTo(at("2026-11-21T06:40+09:00"));
	}

	@Test
	@DisplayName("T25: 같은 시각이라도 스터디 시간대에 따라 스터디 일이 다르다 (11/21 03:00Z → 서울 11/21, 뉴욕 11/20)")
	void studyDayDependsOnStudyZone() {
		Instant receivedAt = at("2026-11-21T03:00Z");
		StudyCalendar newYork = calendar(NEW_YORK, "00:00", 30);

		assertThat(SEOUL_DEFAULT.studyDayOf(receivedAt)).isEqualTo(day("2026-11-21"));
		assertThat(newYork.studyDayOf(receivedAt)).isEqualTo(day("2026-11-20"));
		assertThat(newYork.boundaryOf(day("2026-11-21"))).isEqualTo(at("2026-11-21T00:00-05:00"));
		assertThat(newYork.judgeAt(day("2026-11-20"))).isEqualTo(at("2026-11-21T00:40-05:00"));
	}

	// T25: 서머타임 전환일 (2026년 전환 시각은 JDK 25의 tzdata로 확인)

	static Stream<Arguments> daylightSavingCases() {
		return Stream.of(
				// 3/8 02:00 EST → 03:00 EDT (빈 시각), 11/1 02:00 EDT → 01:00 EST (겹친 시각)
				arguments(NEW_YORK, LocalTime.parse("00:00"), day("2026-03-05"), day("2026-03-11")),
				arguments(NEW_YORK, LocalTime.parse("00:00"), day("2026-10-29"), day("2026-11-04")),
				arguments(NEW_YORK, LocalTime.parse("02:30"), day("2026-03-05"), day("2026-03-11")),
				arguments(NEW_YORK, LocalTime.parse("01:30"), day("2026-10-29"), day("2026-11-04")),
				arguments(NEW_YORK, LocalTime.parse("06:00"), day("2026-03-05"), day("2026-03-11")),
				// 4/5 00:00 → 4/4 23:00 (자정에 겹침), 9/6 00:00 → 01:00 (자정이 없음)
				arguments(SANTIAGO, LocalTime.parse("00:00"), day("2026-04-02"), day("2026-04-08")),
				arguments(SANTIAGO, LocalTime.parse("00:00"), day("2026-09-03"), day("2026-09-09")),
				// 30분 전환: 4/5 02:00 → 01:30 (겹친 시각), 10/4 02:00 → 02:30 (빈 시각)
				arguments(LORD_HOWE, LocalTime.parse("01:45"), day("2026-04-02"), day("2026-04-08")),
				arguments(LORD_HOWE, LocalTime.parse("02:15"), day("2026-10-01"), day("2026-10-07")),
				// 서머타임 없음 (비교용)
				arguments(SEOUL, LocalTime.parse("06:00"), day("2026-11-18"), day("2026-11-24")));
	}

	@ParameterizedTest(name = "T25: {0} 기준 {1}, {2} ~ {3}")
	@MethodSource("daylightSavingCases")
	@DisplayName("T25: 서머타임이 있는 시간대의 전환일 앞뒤에서도 스터디 일이 하루에 한 번씩 바뀐다")
	void studyDayAdvancesOncePerDayAcrossDaylightSavingTransitions(ZoneId zone, LocalTime dayBoundary,
			LocalDate from, LocalDate to) {
		StudyCalendar calendar = calendar(zone, dayBoundary, 30);

		// 날마다: 기준 시각 정각은 그날, 1나노초 전은 전날. 하루 길이는 23~25시간
		for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
			Instant start = calendar.boundaryOf(d);
			Instant end = calendar.boundaryOf(d.plusDays(1));
			assertThat(calendar.studyDayOf(start)).as("%s 기준 시각 정각", d).isEqualTo(d);
			assertThat(calendar.studyDayOf(start.minusNanos(1))).as("%s 기준 시각 1나노초 전", d)
				.isEqualTo(d.minusDays(1));
			assertThat(Duration.between(start, end)).as("%s 스터디 일 길이", d)
				.isBetween(Duration.ofHours(23), Duration.ofHours(25));
		}

		// from 정오부터 to 정오 직전까지 5분 간격으로 훑는다. 정오는 기준 시각(00:00~06:00) 뒤이고 전환 구간과 겹치지 않는다.
		// 스터디 일은 줄지 않고, 바뀔 때는 하루씩만 바뀌며, 늘 [D 기준 시각, D+1 기준 시각) 안에 있다.
		// from에서 시작해 to에서 끝나니, 그 사이의 날을 하루도 건너뛰거나 되풀이하지 않는다
		Instant t = ZonedDateTime.of(from, LocalTime.NOON, zone).toInstant();
		Instant last = ZonedDateTime.of(to, LocalTime.NOON, zone).toInstant();
		LocalDate previous = calendar.studyDayOf(t);
		assertThat(previous).isEqualTo(from);
		while (t.isBefore(last)) {
			LocalDate studyDay = calendar.studyDayOf(t);
			assertThat(studyDay).as("%s의 스터디 일", t).isIn(previous, previous.plusDays(1));
			assertThat(t).as("%s는 스터디 일 %s 안", t, studyDay)
				.isAfterOrEqualTo(calendar.boundaryOf(studyDay))
				.isBefore(calendar.boundaryOf(studyDay.plusDays(1)));
			previous = studyDay;
			t = t.plus(Duration.ofMinutes(5));
		}
		assertThat(previous).isEqualTo(to);
	}

	@Test
	@DisplayName("T25: America/New_York 기준 00:00이면 서머타임이 시작하는 3/8은 23시간, 끝나는 11/1은 25시간")
	void newYorkTransitionDaysAreShorterAndLonger() {
		StudyCalendar calendar = calendar(NEW_YORK, "00:00", 30);

		assertThat(calendar.boundaryOf(day("2026-03-08"))).isEqualTo(at("2026-03-08T00:00-05:00"));
		assertThat(calendar.boundaryOf(day("2026-03-09"))).isEqualTo(at("2026-03-09T00:00-04:00"));
		assertThat(Duration.between(calendar.boundaryOf(day("2026-03-08")), calendar.boundaryOf(day("2026-03-09"))))
			.isEqualTo(Duration.ofHours(23));
		assertThat(Duration.between(calendar.boundaryOf(day("2026-11-01")), calendar.boundaryOf(day("2026-11-02"))))
			.isEqualTo(Duration.ofHours(25));
	}

	@Test
	@DisplayName("T25: 기준 시각이 서머타임으로 없는 시각이면 시계가 건너뛰는 전환 시점에 스터디 일이 바뀐다 (New_York 3/8 02:30 → 03:00 EDT)")
	void boundaryInGapIsTransitionInstant() {
		StudyCalendar calendar = calendar(NEW_YORK, "02:30", 30);
		Instant transition = at("2026-03-08T03:00-04:00");

		assertThat(calendar.boundaryOf(day("2026-03-08"))).isEqualTo(transition);
		assertThat(calendar.studyDayOf(transition)).isEqualTo(day("2026-03-08"));
		assertThat(calendar.studyDayOf(transition.minusNanos(1))).isEqualTo(day("2026-03-07"));
		assertThat(calendar.isInGrace(at("2026-03-08T03:29-04:00"))).isTrue();
		assertThat(calendar.isInGrace(at("2026-03-08T03:30-04:00"))).isFalse();
	}

	@Test
	@DisplayName("T25: 기준 시각이 두 번 있는 시각이면 앞의 것에서 스터디 일이 바뀐다 (New_York 11/1 01:30 EDT)")
	void boundaryInOverlapIsEarlierOffset() {
		StudyCalendar calendar = calendar(NEW_YORK, "01:30", 30);
		Instant secondOccurrence = at("2026-11-01T01:30-05:00");

		assertThat(calendar.boundaryOf(day("2026-11-01"))).isEqualTo(at("2026-11-01T01:30-04:00"));
		assertThat(calendar.studyDayOf(at("2026-11-01T01:29-04:00"))).isEqualTo(day("2026-10-31"));
		// 두 번째 01:30(EST)은 11/1이 시작되고 1시간 뒤라 유예 밖이다
		assertThat(calendar.studyDayOf(secondOccurrence)).isEqualTo(day("2026-11-01"));
		assertThat(calendar.isInGrace(secondOccurrence)).isFalse();
	}

	@Test
	@DisplayName("T25: 자정이 없는 날(America/Santiago 9/6)의 기준 00:00은 그날 처음 오는 01:00이고 LocalDate.atStartOfDay와 같다")
	void midnightGapMatchesAtStartOfDay() {
		StudyCalendar calendar = calendar(SANTIAGO, "00:00", 30);
		LocalDate gapDay = day("2026-09-06");

		assertThat(calendar.boundaryOf(gapDay)).isEqualTo(at("2026-09-06T01:00-03:00"))
			.isEqualTo(gapDay.atStartOfDay(SANTIAGO).toInstant());
	}

	@Test
	@DisplayName("T25: 자정에 시계가 23시로 돌아가는 날(America/Santiago 4/5)은 되풀이되는 23시대도 4/4이고, 4/4는 25시간")
	void midnightOverlapKeepsRepeatedHourInPreviousDay() {
		StudyCalendar calendar = calendar(SANTIAGO, "00:00", 30);

		assertThat(calendar.studyDayOf(at("2026-04-04T23:30-03:00"))).isEqualTo(day("2026-04-04"));
		assertThat(calendar.studyDayOf(at("2026-04-04T23:30-04:00"))).isEqualTo(day("2026-04-04"));
		assertThat(calendar.boundaryOf(day("2026-04-05"))).isEqualTo(at("2026-04-05T00:00-04:00"));
		assertThat(Duration.between(calendar.boundaryOf(day("2026-04-04")), calendar.boundaryOf(day("2026-04-05"))))
			.isEqualTo(Duration.ofHours(25));
	}

	@Test
	@DisplayName("T25: 30분 서머타임(Australia/Lord_Howe)에서도 빈 시각은 전환 시점, 두 번 있는 시각은 앞의 것이 기준 시각이다")
	void halfHourDaylightSaving() {
		// 10/4 02:00(+10:30) → 02:30(+11:00). 02:15는 없는 시각
		assertThat(calendar(LORD_HOWE, "02:15", 30).boundaryOf(day("2026-10-04")))
			.isEqualTo(at("2026-10-04T02:30+11:00"));
		// 4/5 02:00(+11:00) → 01:30(+10:30). 01:45는 두 번 있는 시각
		assertThat(calendar(LORD_HOWE, "01:45", 30).boundaryOf(day("2026-04-05")))
			.isEqualTo(at("2026-04-05T01:45+11:00"));
	}

	@Test
	@DisplayName("S02: 유예·알림 오프셋·판정 여유는 벽시계가 아니라 실제로 흐른 분으로 센다 (New_York 서머타임 전환일)")
	void offsetsAreElapsedMinutes() {
		// 11/1 기준 01:30 EDT + 유예 30분 + 여유 10분 = 02:10 EDT, 곧 01:10 EST
		assertThat(calendar(NEW_YORK, "01:30", 30).judgeAt(day("2026-10-31"))).isEqualTo(at("2026-11-01T01:10-05:00"));
		// 3/8 기준 02:30이 없어 03:00 EDT에 바뀐다. 그 120분 전은 3/8 00:00 EST
		assertThat(calendar(NEW_YORK, "02:30", 30).reminderAt(day("2026-03-07"))).isEqualTo(at("2026-03-08T00:00-05:00"));
	}

}
