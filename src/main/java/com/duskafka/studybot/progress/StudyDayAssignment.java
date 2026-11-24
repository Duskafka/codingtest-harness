package com.duskafka.studybot.progress;

/**
 * 풀이를 스터디 일에 배정한다 (R4, R5, 5.5). 풀이 기록이 새로 생긴 뒤 verification이 부른다 (S08 만들 것 6 '배정 연결 지점').
 *
 * <p>S08은 부르는 자리만 만든다. 배정({@code study_day_solves}, {@code member_days})은 S09가 채운다. 그때까지 구현은
 * 아무것도 하지 않는다 ({@link PendingStudyDayAssignment}).
 *
 * <p>지킬 것
 * <ul>
 * <li>풀이 기록을 넣은 트랜잭션 안에서 동기로 불린다 (pseudocode {@code recordSolve}). 예외를 던지면 풀이 기록도 함께
 * 롤백되고, push라면 수신함이 {@code FAILED}가 된다.</li>
 * <li>새로 생긴 {@code CONFIRMED} 풀이 기록마다 한 번 불린다. 이미 있던 풀이(같은 push를 다시 받음)와 확인 대기에는 불리지 않는다.
 * 확인 대기는 멤버가 문제를 지정할 때 원래 수신 시각으로 배정한다 (R6, S10).</li>
 * </ul>
 */
public interface StudyDayAssignment {

	/** 새 풀이 하나를 배정한다. */
	void assign(SolveToAssign solve);

}
