package com.duskafka.studybot.verification;

/**
 * 문제 플랫폼 (docs/spec/glossary.md {@code Platform}). {@link ProblemRef}의 한쪽 키다.
 *
 * <p>이름이 풀이 기록 등에 문자열로 저장되므로({@code solve_records.platform}) 한번 쓰기 시작한 이름은 바꾸지 않는다.
 */
public enum Platform {

	/** 백준 온라인 저지 (acmicpc.net). */
	BOJ,

	/** 프로그래머스 스쿨 (school.programmers.co.kr). 문제 번호는 lesson ID다. */
	PROGRAMMERS,

	/** LeetCode. 문제 번호는 화면에 보이는 번호(frontend ID)다. */
	LEETCODE

}
