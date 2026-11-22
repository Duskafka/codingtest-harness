package com.duskafka.studybot.verification;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 커밋 하나에서 식별한 문제 하나 ({@link ProblemParser}의 결과).
 *
 * @param ref 문제 식별자
 * @param title 문제 제목. 표시용이고 비교에 쓰지 않는다. 경로나 메시지에서 알 수 없으면 {@code null}
 * @param rawPath 이 문제로 식별한 파일 중 처음 나온 파일의 경로. 정규화하기 전 원본 그대로다 (풀이 기록의 원본 경로)
 */
public record IdentifiedProblem(ProblemRef ref, @Nullable String title, String rawPath) {

	public IdentifiedProblem {
		Objects.requireNonNull(ref, "ref");
		Objects.requireNonNull(rawPath, "rawPath");
	}

}
