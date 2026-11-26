package com.duskafka.studybot.progress;

import java.time.LocalDate;

/**
 * 수동 인증을 그 스터디의 고른 날에 세고 뺀다 (R7, 5.6, S11 만들 것 2·3). 수동 인증을 받는 verification이 부른다 (verification → progress,
 * architecture.md 4.2). 구현은 {@link SelfReportDayCounter}다.
 *
 * <p>push 풀이({@link StudyDayAssignment})와 달리 사용자가 속한 모든 스터디에 R4로 넣지 않는다. 요청한 스터디의 고른 날에만 센다 (사람이 정함,
 * 2026-11-26 S11 세션 "그 스터디의 고른 날만"). 스터디에 관한 확인(멤버, 허용된 날, 수동 인증 방식, 판정 여부)도 여기서 한다. verification은
 * study에 의존하지 않기 때문이다.
 *
 * <p>둘 다 부르는 쪽 트랜잭션 안에서 불린다. 그날의 멤버 일 행을 잠그므로, 부른 뒤 같은 트랜잭션에서 하는 일(하루 5건 세기)은 같은 날의 다른
 * 수동 인증과 한 줄로 선다.
 */
public interface SelfReportCounting {

	/**
	 * 수동 인증 하나를 그 스터디의 고른 날에 센다. 그날 멤버 일 행을 만들고 잠근 뒤 {@code study_day_solves}에 넣고 센 수를 다시 센다.
	 *
	 * <ul>
	 * <li>그 스터디의 (탈퇴하지 않은) 멤버가 아니면 404 (T19).</li>
	 * <li>고른 날이 오늘도, 유예 중인 전날도 아니면 400 (R7, T29).</li>
	 * <li>유예 중인 전날이 R4의 전날 후보가 아니면(판정 대상 아님, 쉬는 날, 면제권 씀, 목표 채움) 409 (사람이 정함 2026-11-26 "R4 전날 조건
	 * 적용").</li>
	 * <li>고른 날 규칙의 수동 인증 방식이 '승인 필요'면 409 (알려진 충돌 6번, 사람이 정함 2026-11-26 "409로 거절").</li>
	 * <li>그날이 이미 판정됐으면 409 (5.6 '판정 후 결과를 바꾸는 일은 이의로만').</li>
	 * <li>그날 같은 문제를 이미 셌으면 409 (R5).</li>
	 * </ul>
	 */
	void count(SelfReportToCount report);

	/**
	 * 철회한 수동 인증의 풀이 기록을 그날 집계에서 뺀다 (R7 '판정 전까지는 본인이 철회'). 그날 멤버 일 행을 잠그고 뺀 뒤 다시 센다. 그날 받은 같은
	 * 문제의 다른 풀이(push 등, 이 스터디에서 아직 세지 않음)가 있으면 빼지 않고 그 풀이로 바꾼다 (사람이 정함 2026-11-26 "다른 풀이로 바꿔
	 * 끼움"). 바꿔 끼울 풀이가 없는데 같은 문제의 push가 다음 날 유예 안에 와 있으면 철회하면 그날 센 문제가 줄므로 409다
	 * ({@code SAME_PROBLEM_PUSHED_IN_GRACE}, 사람이 정함 2026-11-26 "그런 push가 있으면 철회를 막음"). 멤버가 아니면 404, 그날이 이미
	 * 판정됐으면 409 ({@code SELF_REPORT_DAY_JUDGED}).
	 */
	void uncount(long studyId, long userId, LocalDate studyDay, long solveRecordId);

}
