package com.duskafka.studybot.study.service;

import com.duskafka.studybot.study.PenaltyType;
import com.duskafka.studybot.study.SelfReportMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;

/**
 * 규칙 한 버전의 값 (R1). 범위 검사는 요청 검증과 DB CHECK가 한다.
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

}
