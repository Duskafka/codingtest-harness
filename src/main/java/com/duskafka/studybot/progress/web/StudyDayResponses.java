package com.duskafka.studybot.progress.web;

import com.duskafka.studybot.progress.ExemptReason;
import com.duskafka.studybot.progress.JudgmentResult;
import com.duskafka.studybot.progress.MemberDayStatus;
import com.duskafka.studybot.progress.service.StudyDayStatus;
import com.duskafka.studybot.progress.service.StudyDayStatus.MemberStatus;
import com.duskafka.studybot.progress.service.StudyDayStatus.RecentSolve;
import com.duskafka.studybot.study.MembershipRole;
import com.duskafka.studybot.study.StudyDayContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 현황 API의 응답 모양 (S10). 프론트(S16)가 쓰는 계약이라 S10 PR 판단 기록에 '꼭 봐 주세요'로 남긴다 (api.md 머리말).
 */
final class StudyDayResponses {

	private StudyDayResponses() {
	}

	/**
	 * 스터디 일 하루의 현황.
	 *
	 * @param studyId 스터디 ID
	 * @param studyDay 스터디 일
	 * @param today 오늘(지금 시각이 속한 스터디 일)인지
	 * @param startsAt 그날이 시작하는 시각
	 * @param endsAt 그날이 끝나는 시각 (마감)
	 * @param graceEndsAt 늦게 도착한 풀이를 그날로 받아 줄 수 있는 유예가 끝나는 시각 (R4)
	 * @param dailyGoal 그날의 하루 목표
	 * @param restDay 쉬는 요일인지
	 * @param inSeason 기수 안인지. 기수 밖이면 아무도 판정하지 않는다 (R9)
	 * @param summary 판정 대상 멤버의 상태별 수
	 * @param members 그날 멤버였던 사람들 (가입 순서)
	 * @param recentSolves 그날 센 풀이 중 최근에 배정한 50개를 수신 시각이 늦은 것부터
	 */
	record StudyDayStatusResponse(long studyId, LocalDate studyDay, boolean today, Instant startsAt, Instant endsAt,
			Instant graceEndsAt, int dailyGoal, boolean restDay, boolean inSeason, Summary summary,
			List<MemberResponse> members, List<RecentSolveResponse> recentSolves) {

		static StudyDayStatusResponse from(StudyDayStatus status) {
			StudyDayContext day = status.day();
			return new StudyDayStatusResponse(day.studyId(), day.studyDay(), day.studyDay().equals(day.today()),
					day.startsAt(), day.endsAt(), day.graceEndsAt(), day.dailyGoal(), day.restDay(), day.inSeason(),
					new Summary(status.count(MemberDayStatus.DONE), status.count(MemberDayStatus.REMAINING),
							status.count(MemberDayStatus.EXEMPT)),
					status.members().stream().map(MemberResponse::from).toList(),
					status.recentSolves().stream().map(RecentSolveResponse::from).toList());
		}

	}

	/** 판정 대상 멤버의 상태별 수. 판정하지 않는 멤버는 세지 않는다. */
	record Summary(long done, long remaining, long exempt) {
	}

	/**
	 * 멤버 하나.
	 *
	 * @param status {@code DONE}(완료) / {@code REMAINING}(남음) / {@code EXEMPT}(면제) / {@code NOT_JUDGED}(그날 판정하지 않음:
	 * 가입한 날, 기수 밖). 지금 센 수로 정한다
	 * @param exemptReason 면제일 때 까닭: {@code REST_DAY}(쉬는 날) / {@code TICKET}(면제권). 아니면 {@code null}
	 * @param exemptionUsed 면제권을 썼는지. 목표를 채워 완료여도 참이다 (5.7 "면제권 사용" 표시)
	 * @param result 판정 결과 ({@code SUCCESS}, {@code FAIL}, {@code EXEMPT_REST}, {@code EXEMPT_TICKET}). 판정 전이면 {@code null}.
	 * 있으면 화면은 {@code status}보다 이것을 먼저 보여 준다 (이의로 결과가 바뀌면 센 수와 어긋날 수 있다, P2)
	 * @param role 지금 역할. 지난 날 상세에서도 그날의 역할이 아니라 지금(마지막 멤버십의) 역할이다
	 * @param exemptionsRemaining 그날이 속한 기수에 지금 남은 면제권. 기수 밖이면 {@code null} (S11 만들 것 6)
	 * @param selfReportRatio 그날이 속한 기수에 센 풀이 중 수동 인증의 비율(0~1). 방장이 볼 때만 있고, 기수 밖이거나 센 풀이가 없으면
	 * {@code null} (S11 만들 것 4)
	 */
	record MemberResponse(long userId, String login, @Nullable String avatarUrl, MembershipRole role,
			MemberDayStatus status, @Nullable ExemptReason exemptReason, boolean exemptionUsed, int solvedCount,
			@Nullable JudgmentResult result, @Nullable Integer exemptionsRemaining, @Nullable Double selfReportRatio) {

		static MemberResponse from(MemberStatus member) {
			return new MemberResponse(member.userId(), member.login(), member.avatarUrl(), member.role(),
					member.progress().status(), member.progress().exemptReason(), member.exemptionUsed(),
					member.solvedCount(), member.result(), member.exemptionsRemaining(), member.selfReportRatio());
		}

	}

	/**
	 * 그날 센 풀이 하나.
	 *
	 * @param source 출처: {@code GITHUB} / {@code SELF_REPORT}(수동 인증, S11) / {@code RECOVERY}(누락 복구, P2). 현황의 '수동' 표시에
	 * 쓴다
	 * @param title 문제 제목. 모르면 {@code null}
	 * @param receivedAt 인정 시각
	 */
	record RecentSolveResponse(long userId, String login, String platform, String problemNo, @Nullable String title,
			String source, Instant receivedAt) {

		static RecentSolveResponse from(RecentSolve solve) {
			return new RecentSolveResponse(solve.userId(), solve.login(), solve.platform(), solve.problemNo(),
					solve.title(), solve.source(), solve.receivedAt());
		}

	}

}
