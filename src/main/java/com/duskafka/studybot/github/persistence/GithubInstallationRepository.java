package com.duskafka.studybot.github.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GithubInstallationRepository extends JpaRepository<GithubInstallation, Long> {

	/**
	 * {@code installation} created: 설치를 사용자 없이 ACTIVE로 남기고 계정 값과 설치자 여부를 적는다. PostgreSQL
	 * {@code INSERT ... ON CONFLICT} 한 문장이라 같은 설치 이벤트가 동시에 와도 한 행만 남는다.
	 *
	 * <p>이미 행이 있으면 상태({@code status})와 사용자({@code user_id})는 바꾸지 않는다. 정지·정지 해제가 먼저 와서 남긴 행이면
	 * 그 상태를 지키고, 그 행에 비어 있는 계정 값과 설치자 여부만 채운다. 설치자는 created에서만 확인하기 때문이다
	 * (5.2 처리 순서 1, PR #14 2차 리뷰 반영). 삭제가 먼저 와서 남긴 행은 되살리지 않고 계정 값도 채우지 않는다. 다시 쓰이지 않는
	 * 설치라 가입하지 않은 사람일 수 있는 계정 값을 남길 이유가 없다 (security.md 9.8 최소 수집). 이미 채워진 행이면(같은 created가
	 * 다시 옴) 아무것도 바꾸지 않는다.
	 *
	 * @param installedByOwner 계정 주인이 직접 설치했는지 (created의 {@code account.id == sender.id})
	 * @return 새로 넣었거나 비어 있던 값을 채웠으면 1
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			insert into studybot.github_installations as i
				(installation_id, user_id, account_id, account_login, account_type, installed_by_owner, status, created_at,
				updated_at)
			values (:installationId, null, :accountId, :accountLogin, :accountType, :installedByOwner, 'ACTIVE', :now, :now)
			on conflict (installation_id) do update
				set account_id = excluded.account_id, account_login = excluded.account_login,
					account_type = excluded.account_type, installed_by_owner = excluded.installed_by_owner,
					updated_at = excluded.updated_at
				where i.installed_by_owner is null and i.status <> 'DELETED'
			""", nativeQuery = true)
	int recordCreated(@Param("installationId") long installationId, @Param("accountId") long accountId,
			@Param("accountLogin") String accountLogin, @Param("accountType") String accountType,
			@Param("installedByOwner") boolean installedByOwner, @Param("now") Instant now);

	/**
	 * 처음 보는 설치를 이 상태로 남긴다 (created보다 먼저 온 deleted·suspend·unsuspend). 설치 ID와 상태만 남기고 계정 값과
	 * 설치자 여부는 비워 둔다. 그 이벤트의 sender는 설치한 사람이 아니고 {@code installation.account}는 null일 수 있어서다.
	 * 이미 있으면 아무것도 바꾸지 않는다. 다른 트랜잭션이 같은 설치를 막 넣었으면 그 커밋을 기다렸다가 0을 돌려준다.
	 *
	 * @param status {@link InstallationStatus} 이름
	 * @return 새로 넣었으면 1
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			insert into studybot.github_installations (installation_id, status, created_at, updated_at)
			values (:installationId, :status, :now, :now)
			on conflict (installation_id) do nothing
			""", nativeQuery = true)
	int recordStateIfAbsent(@Param("installationId") long installationId, @Param("status") String status,
			@Param("now") Instant now);

	/**
	 * 설치의 레포 목록 리비전을 1 올린다 ({@code installation_repositories} removed). 설치 행을 고치므로, 이 설치로 연결을 저장하는
	 * 트랜잭션({@link #findForUpdate})과 서로 기다린다.
	 *
	 * @return 올렸으면 1, 모르는 설치면 0
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			update studybot.github_installations
			set repositories_revision = repositories_revision + 1, updated_at = :now
			where installation_id = :installationId
			""", nativeQuery = true)
	int bumpRepositoriesRevision(@Param("installationId") long installationId, @Param("now") Instant now);

	/**
	 * 이 GitHub ID의 사용자에게 아직 주인이 없는 설치를 잇는다 (5.2 처리 순서 1·2). 서명된 created 웹훅이 남긴 설치자 정보로만
	 * 판단한다: 개인 계정({@code account_type = 'User'})이고 계정 주인이 직접 설치했으며({@code installed_by_owner}) 삭제되지 않은
	 * 설치. created를 아직 못 본 설치는 {@code installed_by_owner}가 NULL이라 잇지 않는다. 이미 주인이 있는 설치는 바꾸지 않는다.
	 *
	 * @return 이은 설치 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update studybot.github_installations
			set user_id = :userId, updated_at = :now
			where account_id = :githubId
				and account_type = 'User'
				and installed_by_owner
				and user_id is null
				and status <> 'DELETED'
			""", nativeQuery = true)
	int claim(@Param("userId") long userId, @Param("githubId") long githubId, @Param("now") Instant now);

	/** 이 사용자의 지금 설치 (삭제되지 않은 것 중 가장 나중 것). 개인 계정에는 App을 한 번만 설치할 수 있어 보통 하나다. */
	@Query("""
			select i from GithubInstallation i
			where i.userId = :userId and i.status <> com.duskafka.studybot.github.persistence.InstallationStatus.DELETED
			order by i.createdAt desc, i.installationId desc
			limit 1
			""")
	Optional<GithubInstallation> findCurrentByUserId(@Param("userId") long userId);

	/**
	 * 설치 행을 잠그고 읽는다. 설치 웹훅(deleted·suspend·unsuspend)이 상태를 바꿀 때와, 연결을 저장하는 트랜잭션이 설치가 아직
	 * 이 사용자의 ACTIVE 설치이고 레포 목록 리비전이 그대로인지 다시 확인할 때 쓴다. 둘이 겹치면 한쪽이 끝날 때까지 기다린다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from GithubInstallation i where i.installationId = :installationId")
	Optional<GithubInstallation> findForUpdate(@Param("installationId") long installationId);

	/**
	 * 설치 행을 잠그고 지금 DB 값을 읽는다. 연결을 저장하는 트랜잭션이 설치가 아직 이 사용자의 ACTIVE 설치이고 레포 목록 리비전이
	 * 그대로인지 확인할 때 쓴다.
	 *
	 * <p>엔티티를 거치지 않는다. open-in-view로 요청 내내 열려 있는 EntityManager에는 앞서 읽은 설치 엔티티가 남아 있고,
	 * {@link #findForUpdate}는 행을 잠그더라도 그 엔티티를 DB 값으로 다시 채우지 않고 그대로 돌려준다 (PR #14 리뷰 반영).
	 */
	@Query(value = """
			select status as "status", user_id as "userId", repositories_revision as "repositoriesRevision"
			from studybot.github_installations
			where installation_id = :installationId
			for update
			""", nativeQuery = true)
	Optional<LockedInstallation> lockCurrentState(@Param("installationId") long installationId);

	/** 잠그고 읽은 설치의 지금 값. */
	interface LockedInstallation {

		String getStatus();

		@Nullable Long getUserId();

		long getRepositoriesRevision();

	}

}
