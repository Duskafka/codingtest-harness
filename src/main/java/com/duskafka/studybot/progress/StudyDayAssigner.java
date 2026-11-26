package com.duskafka.studybot.progress;

import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.progress.persistence.MemberDayRepository;
import com.duskafka.studybot.progress.persistence.MemberDayRepository.LockedMemberDay;
import com.duskafka.studybot.progress.persistence.StudyDaySolveRepository;
import com.duskafka.studybot.study.MemberStudies;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 풀이를 스터디 일에 배정한다 (R4, R5, 5.5, 부록 D {@code recordSolve}·{@code assignStudyDay}, S09 만들 것 1~6).
 *
 * <p>사용자가 속한 활성 스터디마다(탈퇴하지 않은 멤버십, {@link MemberStudies}) 한 트랜잭션 안에서
 * <ol>
 * <li>계획을 세운다: 시각이 속한 스터디 일 N과, 전날로 배정할 수 있는 후보인지 ({@link AssignmentPlan#of}).</li>
 * <li>잠글 멤버 일 행(후보 전날, N)을 모든 스터디에 걸쳐 모으고, 스터디 일 → 스터디 ID 순서로 행마다
 * {@code INSERT ... ON CONFLICT DO NOTHING} 다음 {@code SELECT ... FOR UPDATE} 한다 ({@link MemberDayKey#LOCK_ORDER}).</li>
 * <li>잠근 전날 행의 지금 상태로 배정할 날을 정하고 ({@link AssignmentPlan#decide}), {@code study_day_solves}에 넣는다
 * (같은 날 같은 문제면 무시, R5). 새로 넣었으면 그 날 멤버 일의 센 수를 다시 센다.</li>
 * </ol>
 *
 * <p>확인 대기 지정({@link #assignIdentified}, S10)은 넣기 전에 배정할 날이 판정됐는지 본다.
 *
 * <p>잠금 순서와 그 이유(교착 방지)는 결정 제안서 007에 있다.
 */
@Component
@RequiredArgsConstructor
class StudyDayAssigner implements StudyDayAssignment {

	/** 확인 대기 지정에서 배정할 날이 이미 판정됐을 때의 409 문장 (S10 만들 것 4 '이의가 필요하다는 안내'). */
	static final String JUDGED_DAY_DETAIL = "이미 판정이 끝난 날의 풀이라 문제를 지정할 수 없어요. 판정을 바로잡으려면 이의를 신청해야 해요.";

	/** 그 409의 {@code code}. 화면은 이 코드일 때만 이의 안내를 보여 준다 (사람이 정함, 2026-11-26 "code 속성 추가"). */
	public static final String JUDGED_DAY_CODE = "STUDY_DAY_JUDGED";

	private final MemberStudies memberStudies;

	private final MemberDayRepository memberDays;

	private final StudyDaySolveRepository studyDaySolves;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void assign(SolveToAssign solve) {
		assign(solve, false);
	}

	/**
	 * 확인 대기에 문제를 지정한 풀이를 배정한다 (R6, S10 만들 것 4). 배정할 날을 모두 정한 뒤, 어느 스터디에서든 다음 중 하나면 아무 데도
	 * 넣지 않고 409를 던진다. 부르는 쪽 트랜잭션이 함께 롤백되어 아무것도 바뀌지 않는다 (사람이 정함, 2026-11-25 S10 세션
	 * "전부 409, 아무것도 안 바꿈"). 판정된 날을 바꾸는 일은 이의(P2)로 한다.
	 * <ul>
	 * <li>배정할 날의 멤버 일이 이미 판정됐다.</li>
	 * <li>유예 안에 받아 전날이 후보였는데 전날이 실패로 판정됐다. 실패는 판정할 때 목표 미달·면제권 없음이었다는 뜻이라, 받은 시각에는
	 * 전날로 갔을 풀이다 (R4·R6 '원래 수신 시각 기준', 사람이 정함, 2026-11-25 S10 세션 "FAIL이면 409"). 전날이 성공이나 면제로
	 * 판정됐으면 R4대로 받은 날로 간다.</li>
	 * </ul>
	 */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void assignIdentified(SolveToAssign solve) {
		assign(solve, true);
	}

	private void assign(SolveToAssign solve, boolean rejectJudgedDay) {
		List<AssignmentPlan> plans = this.memberStudies.activeStudiesOf(solve.userId())
			.stream()
			.map(study -> AssignmentPlan.of(study, solve.receivedAt()))
			.toList();
		Map<MemberDayKey, LockedMemberDay> locked = lockInOrder(solve.userId(), plans);
		List<Decision> decisions = plans.stream().map(plan -> decide(plan, locked)).toList();
		if (rejectJudgedDay && decisions.stream().anyMatch(Decision::judgedDayAtReceipt)) {
			throw new ConflictException(JUDGED_DAY_CODE, JUDGED_DAY_DETAIL);
		}
		for (Decision decision : decisions) {
			int inserted = this.studyDaySolves.insertIfAbsent(decision.studyId(), solve.userId(), decision.day(),
					solve.solveRecordId(), solve.platform(), solve.problemNo());
			if (inserted > 0) {
				this.memberDays.recountSolved(decision.row().getId());
			}
		}
	}

	/** 잠근 전날 행의 지금 상태로 배정할 날을 정한다 ({@link AssignmentPlan#decide}). */
	private static Decision decide(AssignmentPlan plan, Map<MemberDayKey, LockedMemberDay> locked) {
		LocalDate previous = plan.previous();
		LockedMemberDay previousRow = previous == null ? null : locked.get(plan.rowOf(previous));
		LocalDate day = plan.decide(previousRow == null ? null : stateOf(previousRow));
		return new Decision(plan.studyId(), day, locked.get(plan.rowOf(day)),
				previousRow != null && previousRow.getFailed());
	}

	/** 잠글 행을 모든 스터디에 걸쳐 모아 정해진 순서로 만들고 잠근다. */
	private Map<MemberDayKey, LockedMemberDay> lockInOrder(long userId, List<AssignmentPlan> plans) {
		List<MemberDayKey> keys = MemberDayKey
			.inLockOrder(plans.stream().flatMap(plan -> plan.rowsToLock().stream()).toList());
		Map<MemberDayKey, LockedMemberDay> locked = new HashMap<>();
		for (MemberDayKey key : keys) {
			this.memberDays.insertIfAbsent(key.studyId(), userId, key.studyDay(), key.seasonId());
			LockedMemberDay row = this.memberDays.lock(key.studyId(), userId, key.studyDay())
				.orElseThrow(() -> new IllegalStateException("만든 멤버 일 행을 찾지 못했어요: " + key));
			locked.put(key, row);
		}
		return locked;
	}

	private static MemberDayState stateOf(LockedMemberDay row) {
		return new MemberDayState(row.getJudged(), row.getExemptionUsed(), row.getSolvedCount());
	}

	/**
	 * 스터디 하나에서 배정할 날과 그날의 잠근 행.
	 *
	 * @param studyId 스터디 ID
	 * @param day 배정할 스터디 일
	 * @param row 그날의 잠근 멤버 일 행
	 * @param previousCandidateFailed 전날이 후보(유예 안, 판정 대상, 쉬는 날 아님)였고 실패로 판정됐는지
	 */
	private record Decision(long studyId, LocalDate day, LockedMemberDay row, boolean previousCandidateFailed) {

		/** 받은 시각 기준으로 배정했을 날이 이미 판정됐는지: 배정할 날이 판정됐거나, 전날로 갔을 풀이인데 전날이 실패로 판정됐다. */
		boolean judgedDayAtReceipt() {
			return this.row.getJudged() || this.previousCandidateFailed;
		}

	}

}
