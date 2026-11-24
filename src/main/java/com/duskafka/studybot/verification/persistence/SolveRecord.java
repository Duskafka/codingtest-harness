package com.duskafka.studybot.verification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 풀이 기록 한 행 (data-model.md 4.3 {@code solve_records}, 5.3, 5.4).
 *
 * <p>push로 만드는 행은 {@link SolveRecordRepository#insertGithubIfAbsent}가 한 문장으로 넣는다 (같은 push를 다시 받거나 동시에 받아도
 * 한 행만 남게). 이 엔티티는 지금은 읽기와 스키마 확인({@code ddl-auto=validate})에만 쓴다. 확인 대기에 문제를 지정하는 변경은
 * S10이 더한다.
 */
@Entity
@Table(name = "solve_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolveRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, updatable = false)
	private SolveSource source;

	/** {@code verification.Platform} 이름. 확인 대기면 비어 있다. */
	@Column(name = "platform")
	private @Nullable String platform;

	/** 문제 번호 ({@code ProblemRef} 정규형). 확인 대기면 비어 있다. */
	@Column(name = "problem_no")
	private @Nullable String problemNo;

	/** 문제 제목. 표시용이고 비교에 쓰지 않는다 (5.4 '주의할 것'). */
	@Column(name = "title")
	private @Nullable String title;

	/** 인정 시각. push면 서버가 웹훅을 받은 시각이다 (R3, D5). */
	@Column(name = "received_at", nullable = false, updatable = false)
	private Instant receivedAt;

	@Column(name = "commit_sha", updatable = false)
	private @Nullable String commitSha;

	/** GitHub 레포 ID. */
	@Column(name = "repo_id", updatable = false)
	private @Nullable Long repoId;

	@Column(name = "inbox_id", updatable = false)
	private @Nullable Long inboxId;

	/** 문제를 식별한(또는 확인 대기로 남긴) 파일의 원본 경로. 정규화하기 전 그대로다 (5.4). */
	@Column(name = "raw_path", updatable = false)
	private @Nullable String rawPath;

	/** 기록을 만든 파서 버전 ({@code ProblemParser.VERSION}). */
	@Column(name = "parser_version", updatable = false)
	private @Nullable Integer parserVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private SolveStatus status;

}
