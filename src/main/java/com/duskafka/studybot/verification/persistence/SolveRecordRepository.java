package com.duskafka.studybot.verification.persistence;

import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
