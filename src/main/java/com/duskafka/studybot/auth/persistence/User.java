package com.duskafka.studybot.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 사용자 (data-model.md 4.3 {@code users}). 로그인할 때 GitHub에서 받은 값으로 만들거나 갱신한다.
 * 행을 만들고 고치는 일은 {@link UserRepository#upsertByGithubId}가 한 문장으로 한다 (동시에 처음 로그인해도 안전하게).
 * 그래서 이 엔티티는 읽기만 하고, 값을 바꾸는 메서드가 없다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "github_id", nullable = false, unique = true)
	private long githubId;

	@Column(name = "github_login", nullable = false)
	private String githubLogin;

	@Column(name = "avatar_url")
	private @Nullable String avatarUrl;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** 탈퇴(익명화)한 시각. 탈퇴 처리는 S15 (5.16). */
	@Column(name = "deleted_at")
	private @Nullable Instant deletedAt;

}
