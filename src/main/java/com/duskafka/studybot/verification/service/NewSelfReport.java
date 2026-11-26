package com.duskafka.studybot.verification.service;

import com.duskafka.studybot.verification.ProblemRef;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 새 수동 인증 하나 (R7, S11 만들 것 1).
 *
 * @param studyDay 인증할 스터디 일 (오늘, 또는 유예 중인 전날)
 * @param problem 푼 문제
 * @param evidenceUrl 증거 링크 ({@code http}·{@code https}만). 없으면 {@code null}
 * @param memo 메모. 없으면 {@code null}
 */
public record NewSelfReport(LocalDate studyDay, ProblemRef problem, @Nullable String evidenceUrl, @Nullable String memo) {

	public NewSelfReport {
		Objects.requireNonNull(studyDay, "studyDay");
		Objects.requireNonNull(problem, "problem");
	}

}
