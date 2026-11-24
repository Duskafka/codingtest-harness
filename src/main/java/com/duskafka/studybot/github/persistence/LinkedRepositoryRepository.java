package com.duskafka.studybot.github.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 풀이 레포 연결. 설치 이벤트로 여러 연결을 한꺼번에 바꾸는 일괄 UPDATE는 앞서 바꾼 엔티티를 먼저 반영한다(flush).
 * 영속성 컨텍스트를 비우지는 않으니, 일괄 UPDATE 뒤에는 같은 트랜잭션에서 {@link LinkedRepository}를 다시 읽지 않는다.
 */
public interface LinkedRepositoryRepository extends JpaRepository<LinkedRepository, Long> {

	/** 이 사용자의 지금 연결 (ACTIVE 또는 SUSPENDED). 사용자당 하나다 (부분 UNIQUE). */
	@Query("""
			select l from LinkedRepository l
			where l.userId = :userId
				and l.status in (com.duskafka.studybot.github.persistence.LinkedRepositoryStatus.ACTIVE,
					com.duskafka.studybot.github.persistence.LinkedRepositoryStatus.SUSPENDED)
			""")
	Optional<LinkedRepository> findCurrentByUserId(@Param("userId") long userId);

	/**
	 * push를 받은 레포의 활성 연결 (S08, 5.3 'push 처리 순서' 2). 레포당 하나다 (부분 UNIQUE {@code linked_repositories_active_repo_uk}).
	 * 정지(SUSPENDED)·끊긴(INACTIVE) 연결은 찾지 않는다.
	 */
	@Query("""
			select l from LinkedRepository l
			where l.repoId = :repoId
				and l.status = com.duskafka.studybot.github.persistence.LinkedRepositoryStatus.ACTIVE
			""")
	Optional<LinkedRepository> findActiveByRepoId(@Param("repoId") long repoId);

	/**
	 * 설치의 레포 목록 전체를 봤는데 이 레포가 없다: 목록을 읽기 전에 본 그 연결 행이 아직 활성이면 끊는다. 그사이 그 행이 끊겼거나
	 * 다른 연결(새 행)이 생겼으면 아무것도 하지 않는다 (PR #14 2차 리뷰 반영).
	 *
	 * 요청 내내 열려 있는 EntityManager(open-in-view)에 옛 상태의 엔티티가 남지 않게 비운다.
	 *
	 * @param linkId 목록을 읽기 전에 본 연결 행
	 * @param installationId 목록을 읽은 설치
	 * @return 끊었으면 1
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update studybot.linked_repositories set status = 'INACTIVE', updated_at = :now
			where id = :linkId and user_id = :userId and installation_id = :installationId and repo_id = :repoId
				and status = 'ACTIVE'
			""", nativeQuery = true)
	int deactivateActive(@Param("linkId") long linkId, @Param("userId") long userId,
			@Param("installationId") long installationId, @Param("repoId") long repoId, @Param("now") Instant now);

	/** 설치가 삭제됐다: 그 설치의 연결을 모두 끊는다. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			update studybot.linked_repositories set status = 'INACTIVE', updated_at = :now
			where installation_id = :installationId and status in ('ACTIVE', 'SUSPENDED')
			""", nativeQuery = true)
	int deactivateInstallation(@Param("installationId") long installationId, @Param("now") Instant now);

	/** 설치가 정지됐다: 활성 연결을 SUSPENDED로. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			update studybot.linked_repositories set status = 'SUSPENDED', updated_at = :now
			where installation_id = :installationId and status = 'ACTIVE'
			""", nativeQuery = true)
	int suspendInstallation(@Param("installationId") long installationId, @Param("now") Instant now);

	/**
	 * 설치 정지가 풀렸다: 정지로 꺼진 연결만 다시 ACTIVE로. 그동안 같은 레포가 다른 연결로 활성이 됐으면 그 연결은 두지 않고 끊는다
	 * (레포당 활성 하나, 부분 UNIQUE). 정상 흐름에서는 생기지 않는다.
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			update studybot.linked_repositories l
			set status = case when exists (
					select 1 from studybot.linked_repositories other
					where other.repo_id = l.repo_id and other.status = 'ACTIVE' and other.id <> l.id)
				then 'INACTIVE' else 'ACTIVE' end,
				updated_at = :now
			where l.installation_id = :installationId and l.status = 'SUSPENDED'
			""", nativeQuery = true)
	int unsuspendInstallation(@Param("installationId") long installationId, @Param("now") Instant now);

	/** 설치에서 레포가 빠졌다: 그 설치에서 그 레포 ID의 연결을 끊는다. 이름이 아니라 레포 ID로 찾는다. */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			update studybot.linked_repositories set status = 'INACTIVE', updated_at = :now
			where installation_id = :installationId and repo_id in (:repoIds) and status in ('ACTIVE', 'SUSPENDED')
			""", nativeQuery = true)
	int deactivateRepositories(@Param("installationId") long installationId,
			@Param("repoIds") Collection<Long> repoIds, @Param("now") Instant now);

}
