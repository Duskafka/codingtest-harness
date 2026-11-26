package com.duskafka.studybot.verification.persistence;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 내 풀이 한 줄 (조회 전용 DTO 쿼리, OD-04 기본안, S10).
 *
 * @param id 풀이 기록 ID
 * @param source 출처
 * @param status 상태 (확인 대기 포함)
 * @param platform 플랫폼 이름. 확인 대기면 {@code null}
 * @param problemNo 문제 번호. 확인 대기면 {@code null}
 * @param title 문제 제목. 모르면 {@code null}
 * @param receivedAt 인정 시각 (R3)
 * @param rawPath 원본 경로 (push 기록). 확인 대기에서 어떤 파일인지 알아보는 데 쓴다 (5.4)
 */
public record MySolveRow(long id, SolveSource source, SolveStatus status, @Nullable String platform,
		@Nullable String problemNo, @Nullable String title, Instant receivedAt, @Nullable String rawPath) {

}
