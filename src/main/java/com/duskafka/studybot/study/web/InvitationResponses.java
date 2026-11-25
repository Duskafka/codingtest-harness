package com.duskafka.studybot.study.web;

import com.duskafka.studybot.study.MembershipRole;
import com.duskafka.studybot.study.PenaltyType;
import com.duskafka.studybot.study.SelfReportMode;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.StudyRule;
import com.duskafka.studybot.study.service.InvitationService.AcceptedInvitation;
import com.duskafka.studybot.study.service.InvitationService.CreatedInvitation;
import com.duskafka.studybot.study.service.InvitationService.InvitationPreview;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 초대 API 응답 본문 (S05 PR 판단 기록 '꼭 봐 주세요'). 시각은 ISO-8601 UTC다. */
final class InvitationResponses {

	private InvitationResponses() {
	}

	/**
	 * 만든 초대 ({@code POST /api/studies/{id}/invitations}). 토큰 원문은 이 응답에만 있다. 링크 주소는 프론트가 만든다.
	 *
	 * @param id 초대 ID (철회할 때 쓴다)
	 * @param token 토큰 원문
	 * @param expiresAt 만료 시각
	 * @param maxUses 사용 횟수
	 */
	record CreatedInvitationResponse(long id, String token, Instant expiresAt, int maxUses) {

		static CreatedInvitationResponse from(CreatedInvitation created) {
			return new CreatedInvitationResponse(created.id(), created.token(), created.expiresAt(), created.maxUses());
		}

	}

	/**
	 * 초대 미리보기 ({@code GET /api/invitations/{token}}, 공개). 가입 전에 알아야 할 규칙만 담는다.
	 * 공개 엔드포인트라 스터디 ID, 방장·멤버의 신원, 멤버 수, 개인 설정에 가까운 알림 시각은 넣지 않는다.
	 *
	 * @param study 스터디 이름과 시간대
	 * @param rule 지금 규칙 요약
	 * @param upcomingRules 예정된 변경: 적용 시작일과 그날부터의 규칙 요약. 적용 시작일 순서이고 없으면 빈 배열이다. 가입하면
	 * 다음 스터디 일부터 판정하는데 그날부터 바뀔 수 있어 보여 준다 (5.1, 사람이 정함 2026-11-24 "예정 변경도 보여 줌")
	 * @param season 지금 기수 (시작 전이면 1기)
	 * @param expiresAt 초대 만료 시각
	 */
	record InvitationPreviewResponse(StudyPreview study, RulePreview rule, List<UpcomingRulePreview> upcomingRules,
			SeasonPreview season, Instant expiresAt) {

		static InvitationPreviewResponse from(InvitationPreview preview) {
			var study = preview.study();
			return new InvitationPreviewResponse(new StudyPreview(study.getName(), study.zoneId().getId()),
					RulePreview.from(preview.currentRule()),
					preview.upcomingRules().stream().map(UpcomingRulePreview::from).toList(),
					SeasonPreview.from(preview.currentSeason()), preview.expiresAt());
		}

	}

	record StudyPreview(String name, String timezone) {
	}

	/** 규칙 요약. 알림 시각(reminderOffsetMinutes)과 적용 시작일은 뺐다. */
	record RulePreview(int dailyGoal, @JsonFormat(pattern = "HH:mm") LocalTime dayBoundary, int graceMinutes,
			List<DayOfWeek> restDays, PenaltyType penaltyType, int fineAmount, int exemptionsPerSeason,
			SelfReportMode selfReportMode, int seasonLengthDays) {

		static RulePreview from(StudyRule rule) {
			return new RulePreview(rule.getDailyGoal(), rule.getDayBoundary(), rule.getGraceMinutes(),
					List.copyOf(rule.restDays()), rule.getPenaltyType(), rule.getFineAmount(),
					rule.getExemptionsPerSeason(), rule.getSelfReportMode(), rule.getSeasonLengthDays());
		}

	}

	/**
	 * 예정된 변경 하나.
	 *
	 * @param effectiveFrom 적용 시작일
	 * @param rule 그날부터의 규칙 요약
	 */
	record UpcomingRulePreview(LocalDate effectiveFrom, RulePreview rule) {

		static UpcomingRulePreview from(StudyRule rule) {
			return new UpcomingRulePreview(rule.getEffectiveFrom(), RulePreview.from(rule));
		}

	}

	record SeasonPreview(int seq, LocalDate startDay, LocalDate endDay) {

		static SeasonPreview from(Season season) {
			return new SeasonPreview(season.getSeq(), season.getStartDay(), season.getEndDay());
		}

	}

	/**
	 * 수락 결과 ({@code POST /api/invitations/{token}/accept}). 이미 멤버였어도 같은 모양이다.
	 *
	 * @param studyId 가입한 스터디 ID
	 * @param myRole 역할
	 * @param judgedFrom 판정을 시작하는 스터디 일 (R14)
	 */
	record AcceptInvitationResponse(long studyId, MembershipRole myRole, LocalDate judgedFrom) {

		static AcceptInvitationResponse from(AcceptedInvitation accepted) {
			return new AcceptInvitationResponse(accepted.studyId(), accepted.role(), accepted.judgedFrom());
		}

	}

}
