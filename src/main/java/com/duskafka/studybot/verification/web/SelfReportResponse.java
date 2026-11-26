package com.duskafka.studybot.verification.web;

import com.duskafka.studybot.verification.Platform;
import com.duskafka.studybot.verification.persistence.SelfReport;
import com.duskafka.studybot.verification.persistence.SelfReportStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * 수동 인증 하나 (S11). 프론트(S16)가 쓰는 계약이라 S11 PR 판단 기록에 '꼭 봐 주세요'로 남긴다.
 *
 * @param id 수동 인증 ID ({@code DELETE /api/studies/{id}/self-reports/{rid}}에 쓴다)
 * @param studyId 스터디 ID
 * @param studyDay 인증한 스터디 일
 * @param platform 플랫폼
 * @param problemNo 문제 번호
 * @param evidenceUrl 증거 링크. 없으면 {@code null}
 * @param memo 메모. 없으면 {@code null}
 * @param status {@code ACCEPTED}(인정됨) / {@code WITHDRAWN}(철회됨) 등 (3.4)
 * @param createdAt 제출 시각 = 인정 시각 (R3)
 */
record SelfReportResponse(long id, long studyId, LocalDate studyDay, Platform platform, String problemNo,
		@Nullable String evidenceUrl, @Nullable String memo, SelfReportStatus status, Instant createdAt) {

	static SelfReportResponse from(SelfReport report) {
		return new SelfReportResponse(report.getId(), report.getStudyId(), report.getStudyDay(), report.getPlatform(),
				report.getProblemNo(), report.getEvidenceUrl(), report.getMemo(), report.getStatus(),
				report.getCreatedAt());
	}

}
