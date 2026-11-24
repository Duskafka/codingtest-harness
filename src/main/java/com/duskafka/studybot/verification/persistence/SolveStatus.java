package com.duskafka.studybot.verification.persistence;

/**
 * 풀이 기록의 상태 (glossary.md '풀이 기록', {@code solve_records.status}).
 *
 * <ul>
 * <li>{@link #CONFIRMED}: 문제(플랫폼, 번호)를 식별했다.</li>
 * <li>{@link #PENDING_IDENTIFICATION}: 확인 대기. 풀이 파일로 보이지만 문제를 식별하지 못했다. 멤버가 문제를 지정한다 (R6, S10).</li>
 * <li>{@link #IGNORED}: 풀이로 보지 않는다.</li>
 * </ul>
 */
public enum SolveStatus {

	CONFIRMED, PENDING_IDENTIFICATION, IGNORED

}
