package com.duskafka.studybot.verification.persistence;

import com.duskafka.studybot.verification.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 수동 인증 한 행 (data-model.md 4.3 {@code self_reports}, R7, 5.6, S11).
 *
 * <p>새 행은 이 엔티티로 넣는다. 상태를 바꾸는 철회는 행을 잠근 뒤 네이티브 UPDATE로 한다
 * ({@link SelfReportRepository#withdraw}). open-in-view로 열려 있는 영속성 컨텍스트의 옛 값에 기대지 않기 위해서다.
 */
@Entity
@Table(name = "self_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SelfReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "study_id", nullable = false, updatable = false)
	private Long studyId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	/** 인증한 스터디 일 (오늘, 또는 유예 중인 전날). */
	@Column(name = "study_day", nullable = false, updatable = false)
	private LocalDate studyDay;

	@Enumerated(EnumType.STRING)
	@Column(name = "platform", nullable = false, updatable = false)
	private Platform platform;

	/** 문제 번호 ({@code ProblemRef} 정규형). */
	@Column(name = "problem_no", nullable = false, updatable = false)
	private String problemNo;

	/** 증거 링크. {@code http}·{@code https}만. 서버는 접속하지 않는다 (9.7). */
	@Column(name = "evidence_url", updatable = false)
	private @Nullable String evidenceUrl;

	@Column(name = "memo", updatable = false)
	private @Nullable String memo;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private SelfReportStatus status;

	/** 이 수동 인증으로 만든 풀이 기록 (출처 {@code SELF_REPORT}). '바로 인정'은 늘 있다. '승인 필요' 모드를 위해 비어 있을 수 있다. */
	@Column(name = "solve_record_id", updatable = false)
	private @Nullable Long solveRecordId;

	/** 상태를 바꾼 사람 (본인 철회면 본인). */
	@Column(name = "decided_by")
	private @Nullable Long decidedBy;

	@Column(name = "decided_at")
	private @Nullable Instant decidedAt;

	/** 제출 시각 = 인정 시각 (R3). */
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** 바로 인정한 수동 인증 (R7 기본). */
	public static SelfReport accepted(long studyId, long userId, LocalDate studyDay, Platform platform, String problemNo,
			@Nullable String evidenceUrl, @Nullable String memo, long solveRecordId, Instant createdAt) {
		SelfReport report = new SelfReport();
		report.studyId = studyId;
		report.userId = userId;
		report.studyDay = Objects.requireNonNull(studyDay, "studyDay");
		report.platform = Objects.requireNonNull(platform, "platform");
		report.problemNo = Objects.requireNonNull(problemNo, "problemNo");
		report.evidenceUrl = evidenceUrl;
		report.memo = memo;
		report.status = SelfReportStatus.ACCEPTED;
		report.solveRecordId = solveRecordId;
		report.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		return report;
	}

}
