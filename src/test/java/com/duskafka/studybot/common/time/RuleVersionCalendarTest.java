package com.duskafka.studybot.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.duskafka.studybot.common.time.RuleVersionCalendar.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 규칙 버전에 걸친 스터디 일 계산 (S05, R2, OD-08). 스터디 일 D는 {@code [startOf(D), startOf(D+1))}이고, {@code startOf(D)}는
 * D−1의 규칙으로 센 D의 기준 시각이다. 그래서 기준 시각이 바뀌는 새 기수 첫날이 길이 차이(18~30시간)를 떠안는다.
 */
class RuleVersionCalendarTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	private static final LocalDate SEASON_2 = LocalDate.parse("2026-12-19");

	private static StudyTimeRule rule(ZoneId zone, String dayBoundary, int graceMinutes) {
		return new StudyTimeRule(zone, LocalTime.parse(dayBoundary), graceMinutes, 120, Set.of());
	}

	private static Version version(String effectiveFrom, StudyTimeRule rule) {
		return new Version(LocalDate.parse(effectiveFrom), rule);
	}

	private static Instant at(String offsetDateTime) {
		return OffsetDateTime.parse(offsetDateTime).toInstant();
	}

	private static LocalDate day(String date) {
		return LocalDate.parse(date);
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-11-20T23:59:59+09:00", "2026-11-21T00:00:00+09:00", "2026-11-21T05:59:00+09:00",
			"2026-11-21T06:00:00+09:00", "2026-11-21T12:00:00+09:00", "2026-11-22T00:29:00+09:00" })
	@DisplayName("S05: 버전이 하나면 StudyCalendar와 같다 (기준 06:00)")
	void singleVersionMatchesStudyCalendar(String instant) {
		StudyTimeRule rule = rule(SEOUL, "06:00", 30);
		RuleVersionCalendar versions = RuleVersionCalendar.of(List.of(version("2026-11-21", rule)));
		StudyCalendar single = StudyCalendar.of(rule);

		assertThat(versions.studyDayOf(at(instant))).isEqualTo(single.studyDayOf(at(instant)));
		assertThat(versions.nextStudyDayOf(at(instant))).isEqualTo(single.nextStudyDayOf(at(instant)));
		assertThat(versions.startOf(day("2026-11-21"))).isEqualTo(single.boundaryOf(day("2026-11-21")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-03-07T23:30:00-05:00", "2026-03-08T01:59:00-05:00", "2026-03-08T03:00:00-04:00",
			"2026-11-01T00:30:00-04:00", "2026-11-01T01:30:00-05:00", "2026-11-01T02:00:00-05:00" })
	@DisplayName("S05: 서머타임 시간대에서도 버전이 하나면 StudyCalendar와 같다 (뉴욕, 기준 02:00)")
	void singleVersionMatchesStudyCalendarAcrossDaylightSaving(String instant) {
		StudyTimeRule rule = rule(NEW_YORK, "02:00", 30);
		RuleVersionCalendar versions = RuleVersionCalendar.of(List.of(version("2026-01-01", rule)));

		assertThat(versions.studyDayOf(at(instant))).isEqualTo(StudyCalendar.of(rule).studyDayOf(at(instant)));
	}

	@Test
	@DisplayName("S05: 기준 시각이 00:00에서 06:00으로 바뀌면 새 기수 첫날이 30시간이다 (R2 '새 기수 첫날')")
	void laterBoundaryMakesFirstDayOfNewVersionLonger() {
		RuleVersionCalendar calendar = RuleVersionCalendar
			.of(List.of(version("2026-11-21", rule(SEOUL, "00:00", 30)), version("2026-12-19", rule(SEOUL, "06:00", 30))));

		assertThat(calendar.startOf(SEASON_2)).isEqualTo(at("2026-12-19T00:00:00+09:00"));
		assertThat(calendar.startOf(SEASON_2.plusDays(1))).isEqualTo(at("2026-12-20T06:00:00+09:00"));
		assertThat(Duration.between(calendar.startOf(SEASON_2), calendar.startOf(SEASON_2.plusDays(1))))
			.isEqualTo(Duration.ofHours(30));
		// 지난 기수 마지막 날은 옛 규칙 그대로 24시간이다.
		assertThat(Duration.between(calendar.startOf(day("2026-12-18")), calendar.startOf(SEASON_2)))
			.isEqualTo(Duration.ofHours(24));

		assertThat(calendar.studyDayOf(at("2026-12-18T23:59:59+09:00"))).isEqualTo(day("2026-12-18"));
		assertThat(calendar.studyDayOf(at("2026-12-19T00:00:00+09:00"))).isEqualTo(SEASON_2);
		assertThat(calendar.studyDayOf(at("2026-12-20T05:59:59+09:00"))).isEqualTo(SEASON_2);
		assertThat(calendar.studyDayOf(at("2026-12-20T06:00:00+09:00"))).isEqualTo(day("2026-12-20"));
		assertThat(calendar.nextStudyDayOf(at("2026-12-20T03:00:00+09:00"))).isEqualTo(day("2026-12-20"));
	}

	@Test
	@DisplayName("S05: 기준 시각이 06:00에서 00:00으로 바뀌면 새 기수 첫날이 18시간이다 (지난 기수 마지막 날은 06:00까지)")
	void earlierBoundaryMakesFirstDayOfNewVersionShorter() {
		RuleVersionCalendar calendar = RuleVersionCalendar
			.of(List.of(version("2026-11-21", rule(SEOUL, "06:00", 30)), version("2026-12-19", rule(SEOUL, "00:00", 30))));

		assertThat(calendar.startOf(SEASON_2)).isEqualTo(at("2026-12-19T06:00:00+09:00"));
		assertThat(calendar.startOf(SEASON_2.plusDays(1))).isEqualTo(at("2026-12-20T00:00:00+09:00"));
		assertThat(Duration.between(calendar.startOf(SEASON_2), calendar.startOf(SEASON_2.plusDays(1))))
			.isEqualTo(Duration.ofHours(18));

		assertThat(calendar.studyDayOf(at("2026-12-19T03:00:00+09:00"))).isEqualTo(day("2026-12-18"));
		assertThat(calendar.nextStudyDayOf(at("2026-12-19T03:00:00+09:00"))).isEqualTo(SEASON_2);
		assertThat(calendar.studyDayOf(at("2026-12-19T06:00:00+09:00"))).isEqualTo(SEASON_2);
		assertThat(calendar.studyDayOf(at("2026-12-19T23:59:59+09:00"))).isEqualTo(SEASON_2);
		assertThat(calendar.studyDayOf(at("2026-12-20T00:00:00+09:00"))).isEqualTo(day("2026-12-20"));
	}

	@Test
	@DisplayName("S05: 기준 시각이 같고 다른 항목만 바뀐 버전이면 날 길이는 그대로 24시간이다")
	void sameBoundaryKeepsDayLength() {
		RuleVersionCalendar calendar = RuleVersionCalendar
			.of(List.of(version("2026-11-21", rule(SEOUL, "00:00", 30)), version("2026-11-26", rule(SEOUL, "00:00", 90))));

		assertThat(Duration.between(calendar.startOf(day("2026-11-26")), calendar.startOf(day("2026-11-27"))))
			.isEqualTo(Duration.ofHours(24));
		assertThat(calendar.studyDayOf(at("2026-11-26T00:10:00+09:00"))).isEqualTo(day("2026-11-26"));
	}

	@Test
	@DisplayName("S05: 그날의 규칙은 적용 시작일이 그날 이전인 마지막 버전이고, 첫 버전 전이면 첫 버전이다")
	void ruleForPicksLatestEffectiveVersion() {
		StudyTimeRule first = rule(SEOUL, "00:00", 30);
		StudyTimeRule second = rule(SEOUL, "00:00", 60);
		StudyTimeRule third = rule(SEOUL, "06:00", 60);
		// 순서를 섞어 넣어도 적용 시작일 순서로 본다.
		RuleVersionCalendar calendar = RuleVersionCalendar
			.of(List.of(version("2026-12-19", third), version("2026-11-21", first), version("2026-11-26", second)));

		assertThat(calendar.ruleFor(day("2026-11-01"))).isEqualTo(first);
		assertThat(calendar.ruleFor(day("2026-11-21"))).isEqualTo(first);
		assertThat(calendar.ruleFor(day("2026-11-25"))).isEqualTo(first);
		assertThat(calendar.ruleFor(day("2026-11-26"))).isEqualTo(second);
		assertThat(calendar.ruleFor(day("2026-12-18"))).isEqualTo(second);
		assertThat(calendar.ruleFor(day("2026-12-19"))).isEqualTo(third);
	}

	@Test
	@DisplayName("S05: 버전이 없거나, 적용 시작일이 겹치거나, 시간대가 다르면 만들 수 없다")
	void rejectsInvalidVersions() {
		assertThatIllegalArgumentException().isThrownBy(() -> RuleVersionCalendar.of(List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> RuleVersionCalendar.of(
				List.of(version("2026-11-21", rule(SEOUL, "00:00", 30)), version("2026-11-21", rule(SEOUL, "00:00", 60)))));
		assertThatIllegalArgumentException().isThrownBy(() -> RuleVersionCalendar.of(List
			.of(version("2026-11-21", rule(SEOUL, "00:00", 30)), version("2026-12-19", rule(NEW_YORK, "00:00", 30)))));
	}

}
