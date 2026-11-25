package com.duskafka.studybot.progress;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 풀이 기록 하나의 화면용 값 ({@link SolveRecordDetails}).
 *
 * @param solveRecordId 풀이 기록 ID
 * @param source 출처 이름 ({@code solve_records.source}: {@code GITHUB}, {@code SELF_REPORT}, {@code RECOVERY}). 현황의 '수동' 표시에
 * 쓴다 (5.6)
 * @param title 문제 제목. 표시용이고, 모르면 {@code null}
 * @param receivedAt 인정 시각 (R3)
 */
public record SolveDetail(long solveRecordId, String source, @Nullable String title, Instant receivedAt) {

	public SolveDetail {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(receivedAt, "receivedAt");
	}

}
