package com.duskafka.studybot.auth.persistence;

import java.time.Instant;
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

	/** GitHub ID로 탈퇴하지 않은 사용자의 ID를 찾는다. */
	@Query("select u.id from User u where u.githubId = :githubId and u.deletedAt is null")
	Optional<Long> findActiveIdByGithubId(@Param("githubId") long githubId);

}
