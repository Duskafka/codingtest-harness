package com.duskafka.studybot.github.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GithubInstallationRepository extends JpaRepository<GithubInstallation, Long> {

	/**
	 * 설치를 사용자 없이 ACTIVE로 남긴다. 이미 있으면 아무것도 바꾸지 않는다 (같은 created가 다시 와도, 늦게 와도 상태를 되살리지 않게).
	 * PostgreSQL {@code INSERT ... ON CONFLICT DO NOTHING} 한 문장이라 같은 설치 이벤트가 동시에 와도 한 행만 남는다.
	 *
	 * @return 새로 넣었으면 1
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			insert into studybot.github_installations
				(installation_id, user_id, account_id, account_login, account_type, installed_by_owner, status, created_at,
				updated_at)
			values (:installationId, null, :accountId, :accountLogin, :accountType, :installedByOwner, 'ACTIVE', :now, :now)
			on conflict (installation_id) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("installationId") long installationId, @Param("accountId") long accountId,
			@Param("accountLogin") String accountLogin, @Param("accountType") String accountType,
			@Param("installedByOwner") boolean installedByOwner, @Param("now") Instant now);

	/**
	 * 이 GitHub ID의 사용자에게 아직 주인이 없는 설치를 잇는다 (5.2 처리 순서 1·2). 서명된 웹훅이 남긴 설치자 정보로만 판단한다:
	 * 개인 계정({@code account_type = 'User'})이고 계정 주인이 직접 설치했으며({@code installed_by_owner}) 삭제되지 않은 설치.
	 * 이미 주인이 있는 설치는 바꾸지 않는다.
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
	 * 설치 행을 잠그고 읽는다. 연결을 저장하는 트랜잭션이 설치가 아직 이 사용자의 ACTIVE 설치인지 다시 확인할 때 쓴다.
	 * 설치 웹훅(suspend·deleted)도 이 행을 고치므로, 둘이 겹치면 한쪽이 끝날 때까지 기다린다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from GithubInstallation i where i.installationId = :installationId")
	Optional<GithubInstallation> findForUpdate(@Param("installationId") long installationId);

}
