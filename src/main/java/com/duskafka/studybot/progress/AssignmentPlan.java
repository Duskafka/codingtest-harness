package com.duskafka.studybot.progress;

import com.duskafka.studybot.study.MemberStudy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 스터디 하나에서 풀이 하나를 어느 스터디 일에 배정할지 (R4, 5.5, 부록 D {@code assignStudyDay}). 순수 계산이고 DB를 보지 않는다.
 *
 * <p>두 단계로 나눈다.
 * <ol>
 * <li>잠그기 전 ({@link #of}): 시각이 속한 스터디 일 N을 세고, 전날(N−1)이 후보인지 본다. 후보는 바뀌지 않는 조건만으로 정한다:
 * t가 N의 기준 시각부터 N 규칙의 유예 안에 있고, 전날이 이 멤버의 판정 대상(가입·기수, R9)이고, 전날 규칙으로 쉬는 요일이 아니다.</li>
 * <li>전날 멤버 일 행을 잠근 뒤 ({@link #decide}): 아직 판정 전이고, 면제권을 쓰지 않았고, 센 수가 전날 규칙의 목표보다 적으면 전날로,
 * 아니면 N으로 배정한다.</li>
 * </ol>
 *
 * <p>잠글 행은 후보 전날과 N이다 ({@link #rowsToLock}). 전날로 배정하더라도 N을 잠근다. 잠금 순서를 풀이와 상관없이 정해 두기 위해서다
 * (결정 제안서 007).
 *
 * @param studyId 스터디 ID
 * @param today 시각이 속한 스터디 일 (R4의 N)
 * @param todaySeasonId N을 담은 기수 행. 기수 밖이면 {@code null}
 * @param previous 전날로 배정할 수 있는 후보면 N−1, 아니면 {@code null}
 * @param previousSeasonId 후보 전날을 담은 기수 행. 후보가 없으면 {@code null}
 * @param previousGoal 후보 전날 규칙의 하루 목표. 후보가 없으면 0
 */
record AssignmentPlan(long studyId, LocalDate today, @Nullable Long todaySeasonId, @Nullable LocalDate previous,
		@Nullable Long previousSeasonId, int previousGoal) {

	AssignmentPlan {
		Objects.requireNonNull(today, "today");
	}

	/** 잠그기 전에 셀 수 있는 것만으로 계획을 세운다. */
	static AssignmentPlan of(MemberStudy study, Instant receivedAt) {
		throw new UnsupportedOperationException("S09 구현 전");
	}

	/** 잠가야 할 멤버 일 행: 후보 전날(있으면)과 N. */
	List<MemberDayKey> rowsToLock() {
		List<MemberDayKey> rows = new ArrayList<>(2);
		if (this.previous != null) {
			rows.add(new MemberDayKey(this.studyId, this.previous, this.previousSeasonId));
		}
		rows.add(new MemberDayKey(this.studyId, this.today, this.todaySeasonId));
		return rows;
	}

	/**
	 * 잠근 전날 행의 지금 상태로 배정할 날을 정한다.
	 *
	 * @param previousDay 잠근 전날 행. 후보 전날이 없으면 {@code null}
	 */
	LocalDate decide(@Nullable MemberDayState previousDay) {
		throw new UnsupportedOperationException("S09 구현 전");
	}

}
