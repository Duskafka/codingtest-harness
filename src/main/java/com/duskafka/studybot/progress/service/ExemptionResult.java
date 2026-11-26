package com.duskafka.studybot.progress.service;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 면제권을 쓰거나 취소한 뒤의 상태 (S11 만들 것 5·6, 5.7 '남은 면제권 N개').
 *
 * @param studyDay 그 스터디 일 (오늘)
 * @param exemptionUsed 그날 면제권을 쓴 상태인지
 * @param exemptionsPerSeason 기수당 면제권 (그날 규칙)
 * @param exemptionsRemaining 이번 기수에 남은 면제권. 그날이 기수 밖이면 {@code null}
 */
public record ExemptionResult(LocalDate studyDay, boolean exemptionUsed, int exemptionsPerSeason,
		@Nullable Integer exemptionsRemaining) {

	public ExemptionResult {
		Objects.requireNonNull(studyDay, "studyDay");
	}

}
