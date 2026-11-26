package com.duskafka.studybot.auth.persistence;

import com.duskafka.studybot.auth.UserProfile;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * GitHub ID로 사용자를 만들거나, 이미 있으면 GitHub 로그인 이름과 아바타만 갱신하고 사용자 ID를 돌려준다.
	 * 가입 시각({@code created_at})은 처음 만들 때만 정한다.
	 *
	 * <p>PostgreSQL {@code INSERT ... ON CONFLICT}를 한 문장으로 써서, 같은 사람이 동시에 처음 로그인해도
	 * {@code github_id} UNIQUE 위반 없이 행 하나로 끝난다. 쓰기 트랜잭션 안에서 불러야 한다.
	 */
	@Query(value = """
			insert into studybot.users (github_id, github_login, avatar_url, created_at)
			values (:githubId, :githubLogin, :avatarUrl, :createdAt)
			on conflict (github_id) do update
				set github_login = excluded.github_login,
					avatar_url = excluded.avatar_url
			returning id
			""", nativeQuery = true)
	long upsertByGithubId(@Param("githubId") long githubId, @Param("githubLogin") String githubLogin,
			@Param("avatarUrl") @Nullable String avatarUrl, @Param("createdAt") Instant createdAt);

	/** 사용자들의 화면용 정보 (조회 전용 DTO 쿼리, OD-04 기본안). 엔티티를 영속성 컨텍스트에 올리지 않는다. */
	@Query("select new com.duskafka.studybot.auth.UserProfile(u.id, u.githubLogin, u.avatarUrl) from User u where u.id in :ids")
	List<UserProfile> findProfiles(@Param("ids") Collection<Long> ids);

	/** GitHub ID로 탈퇴하지 않은 사용자의 ID를 찾는다. */
	@Query("select u.id from User u where u.githubId = :githubId and u.deletedAt is null")
	Optional<Long> findActiveIdByGithubId(@Param("githubId") long githubId);

}
