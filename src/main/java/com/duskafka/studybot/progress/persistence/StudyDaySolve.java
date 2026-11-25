package com.duskafka.studybot.progress.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 스터디별 풀이 집계 한 행 (data-model.md 4.3 {@code study_day_solves}, glossary {@code StudyDaySolve}). 같은 문제는 같은 스터디 일에
 * 한 번만 센다 (R5, UNIQUE). 행은 {@link StudyDaySolveRepository#insertIfAbsent}가 넣는다. 이 엔티티는 지금은 스키마 확인과 읽기용이다.
 */
@Entity
@Table(name = "study_day_solves")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyDaySolve {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "study_id", nullable = false, updatable = false)
	private Long studyId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "study_day", nullable = false, updatable = false)
	private LocalDate studyDay;

	/** 이 날에 센 풀이 기록 ({@code solve_records.id}). 같은 날 같은 문제의 기록이 여럿이면 먼저 배정한 것이다. */
	@Column(name = "solve_record_id", nullable = false, updatable = false)
	private Long solveRecordId;

	@Column(name = "platform", nullable = false, updatable = false)
	private String platform;

	@Column(name = "problem_no", nullable = false, updatable = false)
	private String problemNo;

}
