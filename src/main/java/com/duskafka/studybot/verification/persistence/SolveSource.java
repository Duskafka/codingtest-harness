package com.duskafka.studybot.verification.persistence;

/**
 * 풀이 기록의 출처 (glossary.md '풀이 기록', {@code solve_records.source}).
 *
 * <ul>
 * <li>{@link #GITHUB}: push 웹훅 (S08).</li>
 * <li>{@link #SELF_REPORT}: 수동 인증 (S11).</li>
 * <li>{@link #RECOVERY}: 누락 복구 (5.14, P2).</li>
 * </ul>
 */
public enum SolveSource {

	GITHUB, SELF_REPORT, RECOVERY

}
