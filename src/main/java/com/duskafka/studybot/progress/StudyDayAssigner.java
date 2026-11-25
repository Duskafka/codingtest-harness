package com.duskafka.studybot.progress;

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
 * <p>잠금 순서와 그 이유(교착 방지)는 결정 제안서 007에 있다.
 */
@Component
@RequiredArgsConstructor
class StudyDayAssigner implements StudyDayAssignment {

	private final MemberStudies memberStudies;

	private final MemberDayRepository memberDays;

	private final StudyDaySolveRepository studyDaySolves;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void assign(SolveToAssign solve) {
		List<AssignmentPlan> plans = this.memberStudies.activeStudiesOf(solve.userId())
			.stream()
			.map(study -> AssignmentPlan.of(study, solve.receivedAt()))
			.toList();
		Map<MemberDayKey, LockedMemberDay> locked = lockInOrder(solve.userId(), plans);
		for (AssignmentPlan plan : plans) {
			LocalDate previous = plan.previous();
			MemberDayState previousState = previous == null ? null : stateOf(locked.get(plan.rowOf(previous)));
			LocalDate day = plan.decide(previousState);
			int inserted = this.studyDaySolves.insertIfAbsent(plan.studyId(), solve.userId(), day,
					solve.solveRecordId(), solve.platform(), solve.problemNo());
			if (inserted > 0) {
				this.memberDays.recountSolved(locked.get(plan.rowOf(day)).getId());
			}
		}
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void assignIdentified(SolveToAssign solve) {
		throw new UnsupportedOperationException("S10 구현 전");
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

}
