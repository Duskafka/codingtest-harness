package com.duskafka.studybot.verification.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolveRecordRepository extends JpaRepository<SolveRecord, Long> {

	/**
	 * push 커밋에서 나온 풀이 기록을 넣고 새 행의 ID를 돌려준다. 같은 사용자·커밋에 같은 문제나 같은 원본 경로가 이미 있으면 아무것도
	 * 하지 않고 빈 값을 돌려준다 (같은 push를 다른 전달 ID로 다시 받음, S08).
	 *
	 * <p>PostgreSQL {@code INSERT ... ON CONFLICT DO NOTHING}을 충돌 대상 없이 써서 두 부분 UNIQUE 인덱스
	 * ({@code solve_records_commit_problem_uk}, {@code solve_records_commit_path_uk}) 모두에서 중복을 무시한다. 같은 push가 동시에
	 * 와도 UNIQUE 위반 없이 한 행만 남는다 (나중 트랜잭션은 앞 트랜잭션이 끝날 때까지 기다린다). 쓰기 트랜잭션 안에서 불러야 한다.
	 *
	 * @param platform {@code CONFIRMED}면 플랫폼 이름, 확인 대기면 {@code null}
	 * @param problemNo {@code CONFIRMED}면 번호, 확인 대기면 {@code null}
	 * @param status {@link SolveStatus} 이름
	 */
	@Query(value = """
			insert into studybot.solve_records
				(user_id, source, platform, problem_no, title, received_at, commit_sha, repo_id, inbox_id, raw_path,
				parser_version, status)
			values (:userId, 'GITHUB', :platform, :problemNo, :title, :receivedAt, :commitSha, :repoId, :inboxId, :rawPath,
				:parserVersion, :status)
			on conflict do nothing
			returning id
			""", nativeQuery = true)
	Optional<Long> insertGithubIfAbsent(@Param("userId") long userId, @Param("platform") @Nullable String platform,
			@Param("problemNo") @Nullable String problemNo, @Param("title") @Nullable String title,
			@Param("receivedAt") Instant receivedAt, @Param("commitSha") String commitSha, @Param("repoId") long repoId,
			@Param("inboxId") long inboxId, @Param("rawPath") String rawPath, @Param("parserVersion") int parserVersion,
			@Param("status") String status);

	/**
	 * 수동 인증으로 풀이 기록을 넣고 새 행의 ID를 돌려준다 (출처 {@code SELF_REPORT}, 상태 {@code CONFIRMED}, R7, S11 만들 것 2). 인정 시각은
	 * 제출 시각이다 (R3). 커밋이 없으니 UNIQUE 인덱스(커밋 기준)에 걸리지 않는다. 쓰기 트랜잭션 안에서 부른다.
	 */
	@Query(value = """
			insert into studybot.solve_records (user_id, source, platform, problem_no, received_at, status)
			values (:userId, 'SELF_REPORT', :platform, :problemNo, :receivedAt, 'CONFIRMED')
			returning id
			""", nativeQuery = true)
	long insertSelfReport(@Param("userId") long userId, @Param("platform") String platform,
			@Param("problemNo") String problemNo, @Param("receivedAt") Instant receivedAt);

	/**
	 * 철회한 수동 인증의 풀이 기록을 풀이로 보지 않게 한다 ({@code IGNORED}, S11 만들 것 3). 내 풀이 목록에서 인정된 풀이로 보이지 않게
	 * 하려는 것이다. 수동 인증으로 만든 내 기록만 바꾼다.
	 *
	 * @return 바뀐 행 수
	 */
	@Modifying
	@Query(value = """
			update studybot.solve_records set status = 'IGNORED'
			where id = :id and user_id = :userId and source = 'SELF_REPORT'
			""", nativeQuery = true)
	int ignoreSelfReport(@Param("id") long id, @Param("userId") long userId);

	/**
	 * 내 풀이 (api.md 4.4 {@code GET /api/me/solves}, S10 만들 것 3). 확인 대기를 포함한다. 수신 시각이 늦은 것부터이고, 정렬은 쿼리에 고정한다
	 * (요청의 정렬 값을 쿼리에 넣지 않음). 인덱스 {@code solve_records_user_received_idx}를 쓴다.
	 *
	 * @param from 이 시각부터 (포함). {@code null}이면 처음부터
	 * @param to 이 시각 전까지 (뺌). {@code null}이면 끝까지
	 * @param status 이 상태만. {@code null}이면 모두
	 */
	@Query(value = """
			select new com.duskafka.studybot.verification.persistence.MySolveRow(s.id, s.source, s.status, s.platform,
				s.problemNo, s.title, s.receivedAt, s.rawPath)
			from SolveRecord s
			where s.userId = :userId
				and (cast(:from as Instant) is null or s.receivedAt >= :from)
				and (cast(:to as Instant) is null or s.receivedAt < :to)
				and (:status is null or s.status = :status)
			order by s.receivedAt desc, s.id desc
			""", countQuery = """
			select count(s) from SolveRecord s
			where s.userId = :userId
				and (cast(:from as Instant) is null or s.receivedAt >= :from)
				and (cast(:to as Instant) is null or s.receivedAt < :to)
				and (:status is null or s.status = :status)
			""")
	Page<MySolveRow> findMine(@Param("userId") long userId, @Param("from") @Nullable Instant from,
			@Param("to") @Nullable Instant to, @Param("status") @Nullable SolveStatus status, Pageable pageable);

	/**
	 * 이 사용자의 풀이 기록 하나를 내 풀이 한 줄로 읽는다. 엔티티를 영속성 컨텍스트에 올리지 않으니 앞서 네이티브로 고친 값을 그대로
	 * 읽는다.
	 */
	@Query("""
			select new com.duskafka.studybot.verification.persistence.MySolveRow(s.id, s.source, s.status, s.platform,
				s.problemNo, s.title, s.receivedAt, s.rawPath)
			from SolveRecord s where s.id = :id and s.userId = :userId
			""")
	Optional<MySolveRow> findRow(@Param("id") long id, @Param("userId") long userId);

	/** 풀이 기록들을 내 풀이 줄로 읽는다 (S10 현황의 최근 풀이, 조회 포트). 쿼리 한 번이다. */
	@Query("""
			select new com.duskafka.studybot.verification.persistence.MySolveRow(s.id, s.source, s.status, s.platform,
				s.problemNo, s.title, s.receivedAt, s.rawPath)
			from SolveRecord s where s.id in :ids
			""")
	List<MySolveRow> findRows(@Param("ids") Collection<Long> ids);

	/**
	 * 이 사용자의 풀이 기록을 잠그고 상태를 읽는다 ({@code SELECT ... FOR UPDATE}). 남의 기록이거나 없으면 빈 값이다. 같은 확인 대기를 동시에
	 * 지정해도 한 번만 바뀌게 지정하는 트랜잭션이 먼저 부른다 (S10 만들 것 4).
	 */
	@Query(value = "select status from studybot.solve_records where id = :id and user_id = :userId for update",
			nativeQuery = true)
	Optional<String> lockStatus(@Param("id") long id, @Param("userId") long userId);

	/**
	 * 이 풀이 기록과 같은 사용자·커밋에 이 문제의 다른 기록이 이미 있는지. 있으면 문제를 지정할 때 UNIQUE
	 * ({@code solve_records_commit_problem_uk})에 걸린다. 커밋이 없는 기록이면 늘 거짓이다.
	 */
	@Query(value = """
			select exists (
				select 1 from studybot.solve_records other
				join studybot.solve_records target on other.user_id = target.user_id and other.commit_sha = target.commit_sha
				where target.id = :id and other.id <> target.id and other.platform = :platform and other.problem_no = :problemNo)
			""", nativeQuery = true)
	boolean existsSameProblemInCommit(@Param("id") long id, @Param("platform") String platform,
			@Param("problemNo") String problemNo);

	/**
	 * 이 사용자의 확인 대기 기록에 문제를 채우고 {@code CONFIRMED}로 바꾼다 (R6, S10 만들 것 4, data-model '풀이 기록 테이블'). 확인
	 * 대기가 아니면 바꾸지 않는다. 제목은 모르므로 비워 둔다.
	 *
	 * @return 바뀐 행 수
	 */
	@Modifying
	@Query(value = """
			update studybot.solve_records set platform = :platform, problem_no = :problemNo, status = 'CONFIRMED'
			where id = :id and user_id = :userId and status = 'PENDING_IDENTIFICATION'
			""", nativeQuery = true)
	int identify(@Param("id") long id, @Param("userId") long userId, @Param("platform") String platform,
			@Param("problemNo") String problemNo);

}
