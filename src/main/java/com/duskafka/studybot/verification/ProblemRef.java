package com.duskafka.studybot.verification;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 문제 식별자: (플랫폼, 문제 번호) (docs/spec/glossary.md {@code ProblemRef}, 5.4). 키는 항상 둘 다다.
 * 백준 1000번과 프로그래머스 1000번은 다른 문제다.
 *
 * <p>번호는 {@code solve_records.problem_no}처럼 문자열이다. 같은 문제가 한 가지로만 적히도록 정규형만 받는다.
 *
 * @param platform 플랫폼
 * @param problemNo 문제 번호. 0으로 시작하지 않는 ASCII 숫자 문자열 (예: LeetCode 경로의 {@code 0001}은 {@code 1})
 */
public record ProblemRef(Platform platform, String problemNo) {

	private static final Pattern CANONICAL_NUMBER = Pattern.compile("[1-9][0-9]*");

	public ProblemRef {
		Objects.requireNonNull(platform, "platform");
		Objects.requireNonNull(problemNo, "problemNo");
		if (!CANONICAL_NUMBER.matcher(problemNo).matches()) {
			throw new IllegalArgumentException("문제 번호는 0으로 시작하지 않는 숫자여야 해요: " + problemNo);
		}
	}

}
