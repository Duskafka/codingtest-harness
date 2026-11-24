package com.duskafka.studybot.progress;

import java.time.Instant;
import java.util.Objects;

/**
 * 배정할 풀이 ({@link StudyDayAssignment}).
 *
 * <p>progress는 verification을 의존하지 않아서(architecture.md 4.2: verification → progress) 문제 식별자를
 * verification의 {@code ProblemRef} 대신 문자열 두 개로 받는다. {@code ProblemRef}를 common으로 옮길지는 S09가 정한다
 * (S03 인계 기록).
 *
 * @param solveRecordId 풀이 기록 ID ({@code solve_records.id})
 * @param userId 푼 사용자
 * @param platform 플랫폼 이름 ({@code solve_records.platform}, 예: {@code BOJ})
 * @param problemNo 문제 번호 ({@code solve_records.problem_no})
 * @param receivedAt 인정 시각. push면 서버가 웹훅을 받은 시각이다 (R3, D5)
 */
public record SolveToAssign(long solveRecordId, long userId, String platform, String problemNo, Instant receivedAt) {

	public SolveToAssign {
		Objects.requireNonNull(platform, "platform");
		Objects.requireNonNull(problemNo, "problemNo");
		Objects.requireNonNull(receivedAt, "receivedAt");
	}

}
