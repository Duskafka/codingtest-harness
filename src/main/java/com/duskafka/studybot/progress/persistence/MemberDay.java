package com.duskafka.studybot.progress.persistence;

import com.duskafka.studybot.progress.JudgmentResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 멤버 일: 멤버 × 스터디 일 (data-model.md 4.3 {@code member_days}, glossary {@code MemberDay}). 진행 상황(센 수, 면제권)과 판정 결과를
 * 담고, 배정(S09)·면제권(S11)·판정(S12)이 잠그는 행이다 (5.5, 5.10).
 *
 * <p>배정은 행을 {@link MemberDayRepository}의 네이티브 쿼리로 만들고 잠그고 고친다. 이 엔티티는 지금은 스키마 확인
 * ({@code ddl-auto=validate})과 다음 단계의 읽기용이다.
 */
@Entity
@Table(name = "member_days")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberDay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "study_id", nullable = false, updatable = false)
	private Long studyId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "study_day", nullable = false, updatable = false)
	private LocalDate studyDay;

	/** 그날을 담은 기수 행. 기수 밖인 날이면 비어 있다 (R9, OD-07). */
	@Column(name = "season_id", updatable = false)
	private @Nullable Long seasonId;

	/** 센 문제 수. 그 날의 {@code study_day_solves} 행 수다. */
	@Column(name = "solved_count", nullable = false)
	private int solvedCount;

	/** 면제권을 쓴 시각 (R8, S11). 쓰지 않았으면 비어 있다. */
	@Column(name = "exemption_used_at")
	private @Nullable Instant exemptionUsedAt;

	/** 판정 결과. 미판정이면 비어 있다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "result")
	private @Nullable JudgmentResult result;

	/** 결과를 정한 곳. 값은 S12에서 정한다. */
	@Column(name = "result_source")
	private @Nullable String resultSource;

	@Column(name = "judged_at")
	private @Nullable Instant judgedAt;

	/** 판정에 쓴 규칙 버전 ({@code study_rules.id}). S12가 채운다. */
	@Column(name = "rule_id")
	private @Nullable Long ruleId;

	@Version
	@Column(name = "version", nullable = false)
	private int version;

}
