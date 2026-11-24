package com.duskafka.studybot.study.web;

import com.duskafka.studybot.study.MembershipRole;
import com.duskafka.studybot.study.PenaltyType;
import com.duskafka.studybot.study.SeasonStatus;
import com.duskafka.studybot.study.SelfReportMode;
import com.duskafka.studybot.study.StudyMode;
import com.duskafka.studybot.study.persistence.MyStudyRow;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.StudyRule;
import com.duskafka.studybot.study.persistence.StudyStatus;
import com.duskafka.studybot.study.service.StudyDetail;
import com.duskafka.studybot.study.service.StudyLimits;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 스터디 API 응답 본문 (프론트가 쓰는 계약이라 S05 PR 판단 기록 '꼭 봐 주세요'). 날짜는 {@code yyyy-MM-dd},
 * 하루 기준 시각은 {@code HH:mm}, 쉬는 요일은 월요일부터 순서대로 {@code DayOfWeek} 이름이다.
 */
final class StudyResponses {

	private StudyResponses() {
	}

	/**
	 * 스터디 상세 ({@code GET /api/studies/{id}}, 만들기·{@code PATCH}·{@code PUT /rules} 응답).
	 *
	 * @param id 스터디 ID
	 * @param name 이름
	 * @param mode 모드
	 * @param timezone 스터디 시간대 ID
	 * @param status 스터디 상태
	 * @param myRole 요청한 사람의 역할
	 * @param ownerUserId 방장의 사용자 ID
	 * @param memberCount 탈퇴하지 않은 멤버 수 (방장 포함)
	 * @param capacity 정원
	 * @param rule 지금 적용되는 규칙 (시작 전이면 첫 버전)
	 * @param upcomingRules 아직 적용되지 않은 예정 버전. 적용 시작일 순서이고 없으면 빈 배열이다. 각 버전은 그날부터의 규칙 전체다
	 * (R2: 다음 스터디 일부터 / 다음 기수부터, 사람이 정함 2026-11-24 "현재 규칙 + 예정된 변경")
	 * @param season 지금 기수 (시작 전이면 1기)
	 */
	record StudyDetailResponse(long id, String name, StudyMode mode, String timezone, StudyStatus status,
			MembershipRole myRole, long ownerUserId, long memberCount, int capacity, RuleResponse rule,
			List<RuleResponse> upcomingRules, SeasonResponse season) {

		static StudyDetailResponse from(StudyDetail detail) {
			var study = detail.study();
			return new StudyDetailResponse(study.getId(), study.getName(), study.getMode(), study.zoneId().getId(),
					study.getStatus(), detail.myRole(), study.getOwnerId(), detail.memberCount(), StudyLimits.CAPACITY,
					RuleResponse.from(detail.currentRule()),
					detail.upcomingRules().stream().map(RuleResponse::from).toList(),
					SeasonResponse.from(detail.currentSeason()));
		}

	}

	/** 규칙 한 버전 (R1). */
	record RuleResponse(LocalDate effectiveFrom, int dailyGoal, @JsonFormat(pattern = "HH:mm") LocalTime dayBoundary,
			int graceMinutes, List<DayOfWeek> restDays, PenaltyType penaltyType, int fineAmount,
			int exemptionsPerSeason, int reminderOffsetMinutes, SelfReportMode selfReportMode, int seasonLengthDays) {

		static RuleResponse from(StudyRule rule) {
			return new RuleResponse(rule.getEffectiveFrom(), rule.getDailyGoal(), rule.getDayBoundary(),
					rule.getGraceMinutes(), List.copyOf(rule.restDays()), rule.getPenaltyType(), rule.getFineAmount(),
					rule.getExemptionsPerSeason(), rule.getReminderOffsetMinutes(), rule.getSelfReportMode(),
					rule.getSeasonLengthDays());
		}

	}

	/** 기수. {@code endDay}는 마지막 스터디 일(포함)이다. */
	record SeasonResponse(int seq, LocalDate startDay, LocalDate endDay, SeasonStatus status) {

		static SeasonResponse from(Season season) {
			return new SeasonResponse(season.getSeq(), season.getStartDay(), season.getEndDay(), season.getStatus());
		}

	}

	/** 내 스터디 목록의 한 줄 ({@code GET /api/studies}). */
	record StudySummaryResponse(long id, String name, StudyMode mode, MembershipRole myRole) {

		static StudySummaryResponse from(MyStudyRow row) {
			return new StudySummaryResponse(row.id(), row.name(), row.mode(), row.role());
		}

	}

}
