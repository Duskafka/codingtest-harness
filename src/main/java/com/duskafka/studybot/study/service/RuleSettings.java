package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.time.StudyTimeRule;
import com.duskafka.studybot.study.PenaltyType;
import com.duskafka.studybot.study.SelfReportMode;
import com.duskafka.studybot.study.persistence.StudyRule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * 규칙 한 버전의 값 (R1). 범위 검사는 요청 검증과 DB CHECK가 한다.
 *
 * <p>규칙을 바꾸면 항목마다 적용 시작이 다르다 (R2, OD-08).
 * <ul>
 * <li>다음 스터디 일부터: 하루 목표, 유예, 쉬는 요일, 알림 시각, 수동 인증 방식</li>
 * <li>다음 기수부터: 하루 기준 시각, 기수 길이, 기수당 면제권, 벌금(방식·금액). 보증금액은 R1에 범위가 없어 아직 받지 않는다
 * (S05 PR #11 열린 질문)</li>
 * </ul>
 *
 * @param dailyGoal 하루 목표 문제 수
 * @param dayBoundary 하루 기준 시각
 * @param graceMinutes 유예(분)
 * @param restDays 쉬는 요일
 * @param penaltyType 벌금 방식
 * @param fineAmount 벌금액(원)
 * @param exemptionsPerSeason 기수당 면제권
 * @param reminderOffsetMinutes 알림 시각(기준 시각 몇 분 전)
 * @param selfReportMode 수동 인증 방식
 * @param seasonLengthDays 기수 길이(일)
 */
public record RuleSettings(int dailyGoal, LocalTime dayBoundary, int graceMinutes, Set<DayOfWeek> restDays,
		PenaltyType penaltyType, int fineAmount, int exemptionsPerSeason, int reminderOffsetMinutes,
		SelfReportMode selfReportMode, int seasonLengthDays) {

	/**
	 * 규칙 마법사 기본값 = R1 표의 '기본값' 열 (5.1 '만들 것'). 혼자 쓰기도 벌금 방식 기본값이 '없음'이라 같다 (R15).
	 */
	public static final RuleSettings DEFAULTS = new RuleSettings(1, LocalTime.MIDNIGHT, 30, Set.of(), PenaltyType.NONE,
			1000, 4, 120, SelfReportMode.IMMEDIATE, 28);

	public RuleSettings {
		Objects.requireNonNull(dayBoundary, "dayBoundary");
		Objects.requireNonNull(penaltyType, "penaltyType");
		Objects.requireNonNull(selfReportMode, "selfReportMode");
		restDays = Set.copyOf(Objects.requireNonNull(restDays, "restDays"));
	}

	/** 규칙 버전 행의 값. */
	public static RuleSettings of(StudyRule rule) {
		return new RuleSettings(rule.getDailyGoal(), rule.getDayBoundary(), rule.getGraceMinutes(), rule.restDays(),
				rule.getPenaltyType(), rule.getFineAmount(), rule.getExemptionsPerSeason(),
				rule.getReminderOffsetMinutes(), rule.getSelfReportMode(), rule.getSeasonLengthDays());
	}

	/** 다음 스터디 일부터 적용하는 항목이 모두 같은지 (R2). */
	public boolean sameNextDayItems(RuleSettings other) {
		return this.dailyGoal == other.dailyGoal && this.graceMinutes == other.graceMinutes
				&& this.restDays.equals(other.restDays) && this.reminderOffsetMinutes == other.reminderOffsetMinutes
				&& this.selfReportMode == other.selfReportMode;
	}

	/** 다음 기수부터 적용하는 항목이 모두 같은지 (R2). */
	public boolean sameNextSeasonItems(RuleSettings other) {
		return this.dayBoundary.equals(other.dayBoundary) && this.seasonLengthDays == other.seasonLengthDays
				&& this.exemptionsPerSeason == other.exemptionsPerSeason && this.penaltyType == other.penaltyType
				&& this.fineAmount == other.fineAmount;
	}

	/** 다음 스터디 일 항목은 이 값으로, 다음 기수 항목은 {@code current}의 값으로 둔 규칙 (기수 중에 다음 날부터 적용할 버전). */
	public RuleSettings withNextSeasonItemsOf(RuleSettings current) {
		return new RuleSettings(this.dailyGoal, current.dayBoundary, this.graceMinutes, this.restDays, current.penaltyType,
				current.fineAmount, current.exemptionsPerSeason, this.reminderOffsetMinutes, this.selfReportMode,
				current.seasonLengthDays);
	}

	/** 이 값의 시간 규칙. 날짜 계산은 {@code common.time}에서 한다. */
	public StudyTimeRule toTimeRule(ZoneId zoneId) {
		return new StudyTimeRule(zoneId, this.dayBoundary, this.graceMinutes, this.reminderOffsetMinutes, this.restDays);
	}

	/** 이 값으로 만든 새 규칙 버전 행. */
	public StudyRule toVersion(long studyId, LocalDate effectiveFrom, Instant createdAt) {
		return StudyRule.builder()
			.studyId(studyId)
			.effectiveFrom(effectiveFrom)
			.dailyGoal(this.dailyGoal)
			.dayBoundary(this.dayBoundary)
			.graceMinutes(this.graceMinutes)
			.restDays(this.restDays)
			.penaltyType(this.penaltyType)
			.fineAmount(this.fineAmount)
			.exemptionsPerSeason(this.exemptionsPerSeason)
			.reminderOffsetMinutes(this.reminderOffsetMinutes)
			.selfReportMode(this.selfReportMode)
			.seasonLengthDays(this.seasonLengthDays)
			.createdAt(createdAt)
			.build();
	}

}
