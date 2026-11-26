package com.duskafka.studybot.progress;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 스터디 하나의 고른 날에 셀 수동 인증 하나 ({@link SelfReportCounting#count}, S11 만들 것 2).
 *
 * @param studyId 스터디 ID
 * @param userId 낸 사용자
 * @param studyDay 고른 스터디 일 (오늘, 또는 유예 중인 전날)
 * @param receivedAt 인정 시각 = 제출 시각 (R3)
 * @param solveRecordId 수동 인증으로 만든 풀이 기록 ({@code solve_records.id}, 출처 {@code SELF_REPORT})
 * @param platform 플랫폼 이름
 * @param problemNo 문제 번호 ({@code ProblemRef} 정규형)
 */
public record SelfReportToCount(long studyId, long userId, LocalDate studyDay, Instant receivedAt, long solveRecordId,
		String platform, String problemNo) {

	public SelfReportToCount {
		Objects.requireNonNull(studyDay, "studyDay");
		Objects.requireNonNull(receivedAt, "receivedAt");
		Objects.requireNonNull(platform, "platform");
		Objects.requireNonNull(problemNo, "problemNo");
	}

}
