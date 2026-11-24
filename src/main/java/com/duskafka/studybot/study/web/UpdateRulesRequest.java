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
 * {@code PUT /api/studies/{id}/rules} 요청: 원하는 규칙 전체 (사람이 정함, 2026-11-24 "규칙 전체를 받음"). 열 항목이 모두 있어야
 * 하고 빠지면 400이다. 실수로 빠진 값이 기본값으로 되돌아가지 않게 한다. 범위는 R1 표로 만들기 요청({@link RuleRequest})과 같다.
 * 쉬는 요일은 빈 배열이면 '없음'이다. 보증금액은 R1에 범위가 없어 아직 받지 않는다.
 */
record UpdateRulesRequest(@Nullable @NotNull @Min(1) @Max(5) Integer dailyGoal,
		@Nullable @NotNull @JsonFormat(pattern = "HH:mm") @DayBoundaryRange LocalTime dayBoundary,
		@Nullable @NotNull @Min(0) @Max(120) Integer graceMinutes, @Nullable @NotNull Set<@NotNull DayOfWeek> restDays,
		@Nullable @NotNull PenaltyType penaltyType, @Nullable @NotNull @Min(0) @Max(10_000) Integer fineAmount,
		@Nullable @NotNull @Min(0) @Max(10) Integer exemptionsPerSeason,
		@Nullable @NotNull @Min(30) @Max(360) Integer reminderOffsetMinutes,
		@Nullable @NotNull SelfReportMode selfReportMode, @Nullable @NotNull @Min(7) @Max(56) Integer seasonLengthDays) {

	/** 검증을 통과한 뒤에만 부른다 (모든 항목이 있음). */
	RuleSettings toSettings() {
		return new RuleSettings(present(this.dailyGoal), present(this.dayBoundary), present(this.graceMinutes),
				present(this.restDays), present(this.penaltyType), present(this.fineAmount),
				present(this.exemptionsPerSeason), present(this.reminderOffsetMinutes), present(this.selfReportMode),
				present(this.seasonLengthDays));
	}

	private static <T> T present(@Nullable T value) {
		if (value == null) {
			throw new IllegalStateException("검증을 통과하지 않은 요청이에요");
		}
		return value;
	}

}
