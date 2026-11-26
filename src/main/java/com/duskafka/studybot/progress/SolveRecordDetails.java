package com.duskafka.studybot.progress;

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

}
