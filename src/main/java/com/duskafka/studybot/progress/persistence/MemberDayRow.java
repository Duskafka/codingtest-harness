package com.duskafka.studybot.progress.persistence;

import com.duskafka.studybot.progress.JudgmentResult;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 현황에 쓰는 멤버 일 한 행 (조회 전용 DTO 쿼리, OD-04 기본안, S10).
 *
 * @param userId 사용자 ID
 * @param solvedCount 센 문제 수
 * @param exemptionUsedAt 면제권을 쓴 시각. 쓰지 않았으면 {@code null}
 * @param result 판정 결과. 미판정이면 {@code null}
 */
public record MemberDayRow(long userId, int solvedCount, @Nullable Instant exemptionUsedAt,
		@Nullable JudgmentResult result) {

}
