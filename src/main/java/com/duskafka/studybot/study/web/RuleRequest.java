package com.duskafka.studybot.study.web;

import com.duskafka.studybot.study.PenaltyType;
import com.duskafka.studybot.study.SelfReportMode;
import com.duskafka.studybot.study.service.RuleSettings;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 규칙 마법사의 값 (R1). 빠진 항목은 R1 기본값({@link RuleSettings#DEFAULTS})을 쓴다. 범위는 R1 표.
 * 하루 기준 시각은 {@code "HH:mm"}이고 분 단위까지만 받는다.
 */
record RuleRequest(@Nullable @Min(1) @Max(5) Integer dailyGoal,
		@Nullable @JsonFormat(pattern = "HH:mm") @DayBoundaryRange LocalTime dayBoundary,
		@Nullable @Min(0) @Max(120) Integer graceMinutes, @Nullable Set<@NotNull DayOfWeek> restDays,
		@Nullable PenaltyType penaltyType, @Nullable @Min(0) @Max(10_000) Integer fineAmount,
		@Nullable @Min(0) @Max(10) Integer exemptionsPerSeason,
		@Nullable @Min(30) @Max(360) Integer reminderOffsetMinutes, @Nullable SelfReportMode selfReportMode,
		@Nullable @Min(7) @Max(56) Integer seasonLengthDays) {

	static final RuleRequest EMPTY = new RuleRequest(null, null, null, null, null, null, null, null, null, null);

	RuleSettings toSettings() {
		RuleSettings d = RuleSettings.DEFAULTS;
		return new RuleSettings(or(this.dailyGoal, d.dailyGoal()), or(this.dayBoundary, d.dayBoundary()),
				or(this.graceMinutes, d.graceMinutes()), or(this.restDays, d.restDays()),
				or(this.penaltyType, d.penaltyType()), or(this.fineAmount, d.fineAmount()),
				or(this.exemptionsPerSeason, d.exemptionsPerSeason()),
				or(this.reminderOffsetMinutes, d.reminderOffsetMinutes()), or(this.selfReportMode, d.selfReportMode()),
				or(this.seasonLengthDays, d.seasonLengthDays()));
	}

	private static <T> T or(@Nullable T value, T defaultValue) {
		return value != null ? value : defaultValue;
	}

}
