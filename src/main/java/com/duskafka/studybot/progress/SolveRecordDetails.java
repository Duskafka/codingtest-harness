package com.duskafka.studybot.progress;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 풀이 기록의 화면용 값(출처·제목·수신 시각)을 읽는 조회 포트 (S10 오늘 현황의 '최근 풀이').
 *
 * <p>풀이 기록은 verification에 있고 progress는 verification을 의존하지 않는다 (architecture.md 4.2: verification → progress). 그래서
 * 인터페이스를 progress에 두고 verification이 구현한다. 의존 방향은 그대로 verification → progress다 (사람이 정함, 2026-11-25 S10 세션
 * "조회 포트").
 */
public interface SolveRecordDetails {

	/**
	 * 풀이 기록들의 화면용 값. 쿼리 한 번으로 가져온다 (S10 수용 기준 2). 없는 ID는 빠진다. 순서는 정하지 않는다.
	 *
	 * @param solveRecordIds {@code solve_records.id}
	 */
	List<SolveDetail> detailsOf(Collection<Long> solveRecordIds);

	/**
	 * 수동 인증을 철회할 때 대신 셀 수 있는 풀이 기록들 (S11, 사람이 정함 2026-11-26 "다른 풀이로 바꿔 끼움"). 그 사용자가 시각 범위 안에 받은 그
	 * 문제의 풀이 기록 중, 문제를 식별했고({@code CONFIRMED}) 수동 인증이 아닌 것(push, 누락 복구)이다. 수동 인증은 그 스터디의 고른 날에만
	 * 세므로(사람이 정함 "그 스터디의 고른 날만") 다른 스터디의 수동 인증을 끌어오지 않는다. 받은 시각, ID 순서다. 쿼리 한 번이다.
	 *
	 * @param receivedFrom 이 시각부터 (포함)
	 * @param receivedTo 이 시각 전까지 (뺌)
	 */
	List<Long> countableSolveIds(long userId, String platform, String problemNo, Instant receivedFrom,
			Instant receivedTo);

}
