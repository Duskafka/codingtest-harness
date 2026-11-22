package com.duskafka.studybot.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 시간 규칙 값의 R1 범위 검증 (S02 만들 것 1번). */
class StudyTimeRuleTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static StudyTimeRule rule(String dayBoundary, int graceMinutes, int reminderOffsetMinutes) {
		return new StudyTimeRule(SEOUL, LocalTime.parse(dayBoundary), graceMinutes, reminderOffsetMinutes, Set.of());
	}

	@Test
	@DisplayName("S02: R1 범위 밖 값(유예 121분, 기준 시각 07:00, 알림 오프셋 20분)으로는 시간 규칙을 만들 수 없다")
	void outOfRangeValuesAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> rule("00:00", 121, 120));
		assertThatIllegalArgumentException().isThrownBy(() -> rule("07:00", 30, 120));
		assertThatIllegalArgumentException().isThrownBy(() -> rule("00:00", 30, 20));
	}

	@ParameterizedTest(name = "S02: 기준 시각 {0}, 유예 {1}분, 알림 오프셋 {2}분은 R1 범위 안이다")
	@CsvSource({ "00:00, 30, 120", "06:00, 30, 120", "00:00, 0, 120", "00:00, 120, 120", "00:00, 30, 30",
			"00:00, 30, 360", "06:00, 120, 360" })
	@DisplayName("S02: R1 범위의 양 끝 값으로는 시간 규칙을 만들 수 있다")
	void boundaryValuesInsideRangeAreAccepted(String dayBoundary, int graceMinutes, int reminderOffsetMinutes) {
		StudyTimeRule rule = rule(dayBoundary, graceMinutes, reminderOffsetMinutes);

		assertThat(rule.dayBoundary()).isEqualTo(LocalTime.parse(dayBoundary));
		assertThat(rule.grace()).isEqualTo(Duration.ofMinutes(graceMinutes));
		assertThat(rule.reminderOffset()).isEqualTo(Duration.ofMinutes(reminderOffsetMinutes));
	}

	@ParameterizedTest(name = "S02: 기준 시각 {0}, 유예 {1}분, 알림 오프셋 {2}분은 R1 범위 밖이다")
	@CsvSource({ "06:00:00.000000001, 30, 120", "00:00, -1, 120", "00:00, 121, 120",
			"00:00, 30, 29", "00:00, 30, 361" })
	@DisplayName("S02: R1 범위 바로 바깥 값으로는 시간 규칙을 만들 수 없다")
	void valuesJustOutsideRangeAreRejected(String dayBoundary, int graceMinutes, int reminderOffsetMinutes) {
		assertThatIllegalArgumentException().isThrownBy(() -> rule(dayBoundary, graceMinutes, reminderOffsetMinutes));
	}

	@Test
	@DisplayName("S02: 빈 값(null)으로는 시간 규칙을 만들 수 없다")
	void nullValuesAreRejected() {
		LocalTime midnight = LocalTime.MIDNIGHT;
		assertThatNullPointerException().isThrownBy(() -> new StudyTimeRule(null, midnight, 30, 120, Set.of()));
		assertThatNullPointerException().isThrownBy(() -> new StudyTimeRule(SEOUL, null, 30, 120, Set.of()));
		assertThatNullPointerException().isThrownBy(() -> new StudyTimeRule(SEOUL, midnight, 30, 120, null));
	}

	@Test
	@DisplayName("S02: 쉬는 요일은 복사해 두어 바깥에서 바꿔도 영향이 없고, 꺼낸 값도 바꿀 수 없다")
	void restDaysAreCopiedAndUnmodifiable() {
		EnumSet<DayOfWeek> restDays = EnumSet.of(DayOfWeek.SUNDAY);
		StudyTimeRule rule = new StudyTimeRule(SEOUL, LocalTime.MIDNIGHT, 30, 120, restDays);

		restDays.add(DayOfWeek.MONDAY);

		assertThat(rule.restDays()).containsExactly(DayOfWeek.SUNDAY);
		assertThatThrownBy(() -> rule.restDays().add(DayOfWeek.TUESDAY))
			.isInstanceOf(UnsupportedOperationException.class);
	}

}
