package com.duskafka.studybot.progress;

/**
 * 풀이를 스터디 일에 배정한다 (R4, R5, 5.5). 풀이 기록이 새로 생긴 뒤 verification이 부른다 (S08 만들 것 6 '배정 연결 지점').
 *
 * <p>구현은 {@link StudyDayAssigner}다 (S09). 사용자가 속한 활성 스터디마다 스터디 일을 정하고 {@code study_day_solves}·
 * {@code member_days}에 반영한다.
 *
 * <p>지킬 것
 * <ul>
 * <li>풀이 기록을 넣은 트랜잭션 안에서 동기로 불린다 (pseudocode {@code recordSolve}). 예외를 던지면 풀이 기록도 함께
 * 롤백되고, push라면 수신함이 {@code FAILED}가 된다.</li>
 * <li>새로 생긴 {@code CONFIRMED} 풀이 기록마다 한 번 불린다. 이미 있던 풀이(같은 push를 다시 받음)와 확인 대기에는 불리지 않는다.
 * 확인 대기는 멤버가 문제를 지정할 때 원래 수신 시각으로 {@link #assignIdentified}가 배정한다 (R6, S10).</li>
 * <li>한 트랜잭션에서 여러 번 부를 때는 인정 시각이 같은 풀이만 부른다 (push 하나의 커밋들). 멤버 일 행을 정해진 순서로 잠가서 교착을
 * 피하는데, 그 순서는 풀이 하나 안에서 정해진다. 인정 시각이 다른 풀이를 한 트랜잭션에서 배정하면 순서가 섞일 수 있다 (결정 제안서 007).</li>
 * </ul>
 */
public interface StudyDayAssignment {

	/** 새 풀이 하나를 배정한다. */
	void assign(SolveToAssign solve);

	/**
	 * 확인 대기에 멤버가 문제를 지정한 풀이 하나를 원래 수신 시각으로 배정한다 (R6, S10 만들 것 4). 배정하는 방법은 {@link #assign}과 같다.
	 * 다만 어느 스터디에서든 배정할 날이 이미 판정됐으면 아무 데도 넣지 않고 409({@code ConflictException})를 던진다.
	 */
	void assignIdentified(SolveToAssign solve);

}
