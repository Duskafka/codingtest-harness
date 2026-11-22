package com.duskafka.studybot.verification;

/**
 * 문제 식별자: (플랫폼, 문제 번호) (docs/spec/glossary.md {@code ProblemRef}, 5.4). 키는 항상 둘 다다.
 * 백준 1000번과 프로그래머스 1000번은 다른 문제다.
 *
 * @param platform 플랫폼
 * @param problemNo 문제 번호. 0으로 시작하지 않는 ASCII 숫자 문자열 (예: LeetCode 경로의 {@code 0001}은 {@code 1})
 */
public record ProblemRef(Platform platform, String problemNo) {

}
