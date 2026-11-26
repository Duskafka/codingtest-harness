package com.duskafka.studybot.progress;

import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.progress.persistence.CountedSolveRow;
import com.duskafka.studybot.progress.persistence.MemberDayRepository;
import com.duskafka.studybot.progress.persistence.MemberDayRepository.LockedMemberDay;
import com.duskafka.studybot.progress.persistence.StudyDaySolveRepository;
import com.duskafka.studybot.study.MemberStudies;
import com.duskafka.studybot.study.MemberStudy;
import com.duskafka.studybot.study.MemberStudyRules;
import com.duskafka.studybot.study.SelfReportMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수동 인증을 그 스터디의 고른 날에 세고 뺀다 ({@link SelfReportCounting}, S11 만들 것 2·3).
 *
 * <p>멤버 일 행 하나만 잠근다. 결정 제안서 007의 전역 순서(스터디 일 → 스터디 ID)에서 행 하나는 늘 순서를 지킨다. 철회는 풀이 기록 행을 먼저
 * 고치고 이 클래스를 부른다 (풀이 기록 → 멤버 일, S10 {@code MySolveService}).
 *
 * <p>PR #29 열린 질문 결정 (사람이 정함, 2026-11-26, 세션 선택지):
 * <ul>
 * <li>"R4 전날 조건 적용": 유예 중 전날은 R4의 전날 후보일 때만 받는다 ({@link AssignmentPlan}). 늦은 풀이는 push든 수동이든 같은 규칙이고,
 * 면제권을 쓴 전날을 자정 뒤 수동 인증으로 '성공 + 면제권 반환'으로 만들 수 없다.</li>
 * <li>"다른 풀이로 바꿔 끼움": 철회할 때 그날 받은 같은 문제의 다른 풀이(R5 때문에 세지 않은 push)가 있으면 그 풀이가 대신 센다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class SelfReportDayCounter implements SelfReportCounting {

	static final String NOT_FOUND_DETAIL = "스터디를 찾을 수 없어요.";

	static final String DAY_NOT_ALLOWED_DETAIL = "수동 인증은 오늘, 또는 유예 중인 전날만 할 수 있어요.";

	/** 규칙이 '승인 필요'일 때의 409 {@code code} (사람이 정함, 2026-11-26 S11 세션 "409로 거절"). */
	public static final String APPROVAL_UNSUPPORTED_CODE = "SELF_REPORT_APPROVAL_UNSUPPORTED";

	static final String APPROVAL_UNSUPPORTED_DETAIL = "이 스터디는 수동 인증에 방장 승인이 필요한데, 승인 기능은 아직 없어요. 방장에게 '바로 인정'으로 바꿔 달라고 해 주세요.";

	/**
	 * 제출: 고른 날이 판정됐을 때. {@code code}는 S10의 {@code STUDY_DAY_JUDGED}를 그대로 쓴다. 판정된 날에 풀이를 더하려는 것이라 S10 확인 대기
	 * 지정처럼 이의 안내가 맞다 (api.md 4.4 '오류 code').
	 */
	static final String JUDGED_DAY_DETAIL = "이미 판정이 끝난 날이에요. 판정을 바로잡으려면 이의를 신청해야 해요.";

	/** 철회: 그날이 판정된 뒤. 이의 안내가 맞지 않아 {@code STUDY_DAY_JUDGED}와 다른 {@code code}를 쓴다. */
	public static final String WITHDRAW_JUDGED_CODE = "SELF_REPORT_DAY_JUDGED";

	static final String WITHDRAW_JUDGED_DETAIL = "판정이 끝난 날의 수동 인증은 철회할 수 없어요.";

	/**
	 * 유예 중 전날이 R4의 전날 후보가 아닐 때(판정 대상 아님, 쉬는 날, 면제권 씀, 목표 채움)의 409 {@code code}. 화면은 오늘로 내라고 안내한다
	 * (사람이 정함, 2026-11-26 "R4 전날 조건 적용").
	 */
	public static final String PREVIOUS_DAY_NOT_OPEN_CODE = "PREVIOUS_DAY_NOT_OPEN";

	static final String PREVIOUS_DAY_NOT_OPEN_DETAIL = "전날은 이미 목표를 채웠거나 면제권을 썼거나 판정하지 않는 날이라 늦은 풀이를 받지 않아요. 오늘로 내 주세요.";

	/** 그날 같은 문제를 이미 셌을 때의 409 {@code code} (R5). */
	public static final String ALREADY_COUNTED_CODE = "PROBLEM_ALREADY_COUNTED";

	static final String ALREADY_COUNTED_DETAIL = "그날 이미 센 문제예요.";

	private final MemberStudies memberStudies;

	private final MemberDayRepository memberDays;

	private final StudyDaySolveRepository studyDaySolves;

	private final SolveRecordDetails solveRecordDetails;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void count(SelfReportToCount report) {
		MemberStudyRules rules = this.memberStudies.rulesOf(report.studyId(), report.userId())
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL));
		MemberStudy study = rules.study();
		LocalDate day = report.studyDay();
		if (!isAllowedDay(study, day, report)) {
			throw new BadRequestException(DAY_NOT_ALLOWED_DETAIL);
		}
		if (rules.termsOn(day).selfReportMode() == SelfReportMode.APPROVAL_REQUIRED) {
			throw new ConflictException(APPROVAL_UNSUPPORTED_CODE, APPROVAL_UNSUPPORTED_DETAIL);
		}
		boolean previousDay = !day.equals(study.studyDayOf(report.receivedAt()));
		AssignmentPlan plan = AssignmentPlan.of(study, report.receivedAt());
		if (previousDay && plan.previous() == null) {
			throw new ConflictException(PREVIOUS_DAY_NOT_OPEN_CODE, PREVIOUS_DAY_NOT_OPEN_DETAIL);
		}
		this.memberDays.insertIfAbsent(report.studyId(), report.userId(), day, study.seasonIdOn(day));
		LockedMemberDay row = lockOrThrow(report.studyId(), report.userId(), day);
		if (row.getJudged()) {
			throw new ConflictException(StudyDayAssigner.JUDGED_DAY_CODE, JUDGED_DAY_DETAIL);
		}
		if (previousDay && !day.equals(plan.decide(StudyDayAssigner.stateOf(row)))) {
			throw new ConflictException(PREVIOUS_DAY_NOT_OPEN_CODE, PREVIOUS_DAY_NOT_OPEN_DETAIL);
		}
		int inserted = this.studyDaySolves.insertIfAbsent(report.studyId(), report.userId(), day,
				report.solveRecordId(), report.platform(), report.problemNo());
		if (inserted == 0) {
			throw new ConflictException(ALREADY_COUNTED_CODE, ALREADY_COUNTED_DETAIL);
		}
		this.memberDays.recountSolved(row.getId());
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void uncount(long studyId, long userId, LocalDate studyDay, long solveRecordId) {
		MemberStudy study = this.memberStudies.rulesOf(studyId, userId)
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL))
			.study();
		LockedMemberDay row = this.memberDays.lock(studyId, userId, studyDay).orElse(null);
		if (row == null) {
			return;
		}
		if (row.getJudged()) {
			throw new ConflictException(WITHDRAW_JUDGED_CODE, WITHDRAW_JUDGED_DETAIL);
		}
		CountedSolveRow counted = this.studyDaySolves.findCounted(studyId, userId, studyDay, solveRecordId).orElse(null);
		if (counted == null) {
			return;
		}
		Long replacement = replacementFor(study, studyId, studyDay, counted);
		if (replacement != null) {
			this.studyDaySolves.replaceSolve(studyId, userId, studyDay, solveRecordId, replacement);
			return;
		}
		if (this.studyDaySolves.deleteSolve(studyId, userId, studyDay, solveRecordId) > 0) {
			this.memberDays.recountSolved(row.getId());
		}
	}

	/**
	 * 철회한 풀이 대신 그날 셀 풀이 (사람이 정함, 2026-11-26 "다른 풀이로 바꿔 끼움"). 그 스터디 일 안({@code [startOf(day), startOf(day+1))})에
	 * 받은 같은 문제의 풀이 중 수동 인증이 아니고 이 스터디에서 어느 날에도 아직 세지 않은 첫 번째다. 그날 받은 push는 R4로 그날에 왔다가 R5 때문에
	 * 빠진 것이다. 유예로 전날에 갔어야 할 드문 경우도 그날로 둔다. 없으면 {@code null}.
	 */
	private @Nullable Long replacementFor(MemberStudy study, long studyId, LocalDate studyDay, CountedSolveRow counted) {
		List<Long> candidates = this.solveRecordDetails.countableSolveIds(counted.userId(), counted.platform(),
				counted.problemNo(), study.calendar().startOf(studyDay), study.calendar().startOf(studyDay.plusDays(1)));
		if (candidates.isEmpty()) {
			return null;
		}
		Set<Long> alreadyCounted = new HashSet<>(this.studyDaySolves.findCountedAmong(studyId, candidates));
		return candidates.stream().filter(id -> !alreadyCounted.contains(id)).findFirst().orElse(null);
	}

	/**
	 * 오늘(제출 시각이 속한 스터디 일)이거나, 제출 시각이 오늘의 유예 안이고 고른 날이 전날이다 (R7, 5.6 '지난 날짜를 마음대로 고를 수
	 * 없게'). 유예는 오늘(N)의 규칙이다 (R4).
	 */
	private static boolean isAllowedDay(MemberStudy study, LocalDate day, SelfReportToCount report) {
		LocalDate today = study.studyDayOf(report.receivedAt());
		return day.equals(today) || (day.equals(today.minusDays(1)) && study.isInGrace(report.receivedAt()));
	}

	private LockedMemberDay lockOrThrow(long studyId, long userId, LocalDate day) {
		return this.memberDays.lock(studyId, userId, day)
			.orElseThrow(() -> new IllegalStateException(
					"만든 멤버 일 행을 찾지 못했어요: study=" + studyId + ", day=" + day));
	}

}
