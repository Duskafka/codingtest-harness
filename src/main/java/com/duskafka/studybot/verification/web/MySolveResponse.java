package com.duskafka.studybot.verification.web;

import com.duskafka.studybot.verification.persistence.MySolveRow;
import com.duskafka.studybot.verification.persistence.SolveSource;
import com.duskafka.studybot.verification.persistence.SolveStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 내 풀이 한 줄 (S10). 프론트(S16)가 쓰는 계약이라 S10 PR 판단 기록에 '꼭 봐 주세요'로 남긴다.
 *
 * @param id 풀이 기록 ID ({@code PATCH /api/me/solves/{id}}에 쓴다)
 * @param source 출처: {@code GITHUB} / {@code SELF_REPORT} / {@code RECOVERY}
 * @param status {@code CONFIRMED} / {@code PENDING_IDENTIFICATION}(확인 대기) / {@code IGNORED}
 * @param platform 플랫폼. 확인 대기면 {@code null}
 * @param problemNo 문제 번호. 확인 대기면 {@code null}
 * @param title 문제 제목. 모르면 {@code null}
 * @param receivedAt 인정 시각 (R3)
 * @param rawPath push로 받은 파일의 원본 경로. 확인 대기에서 어떤 풀이인지 알아보는 데 쓴다. 수동 인증이면 {@code null}
 */
record MySolveResponse(long id, SolveSource source, SolveStatus status, @Nullable String platform,
		@Nullable String problemNo, @Nullable String title, Instant receivedAt, @Nullable String rawPath) {

	static MySolveResponse from(MySolveRow row) {
		return new MySolveResponse(row.id(), row.source(), row.status(), row.platform(), row.problemNo(), row.title(),
				row.receivedAt(), row.rawPath());
	}

}
