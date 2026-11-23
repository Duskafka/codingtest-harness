package com.duskafka.studybot.github.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.Nullable;

/**
 * 사용자가 고른 풀이 레포 (data-model.md 4.3 {@code linked_repositories}, 5.2). 이름이 아니라 {@code repoId}로 추적한다.
 * 연결을 바꾸거나 끊어도 행을 지우지 않고 상태만 바꾼다. 활성 연결은 레포당 하나, 사용자당 하나다 (DB 부분 UNIQUE).
 *
 * <p>설치 이벤트로 여러 연결을 한꺼번에 바꿀 때는 {@link LinkedRepositoryRepository}의 일괄 UPDATE를 쓴다. 바뀐 컬럼만
 * UPDATE하므로({@code @DynamicUpdate}), 이름·기본 브랜치를 고치는 동안 웹훅이 끊은 상태를 읽어 둔 옛 값으로 되살리지 않는다
 * (PR #14 리뷰 반영).
 */
@Entity
@Table(name = "linked_repositories")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkedRepository {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "installation_id", nullable = false, updatable = false)
	private Long installationId;

	/** GitHub의 레포 ID. 이름이 바뀌어도 그대로다. */
	@Column(name = "repo_id", nullable = false, updatable = false)
	private Long repoId;

	/** 마지막으로 본 {@code owner/name}. 바뀌면 {@link #refresh}로 고친다. */
	@Column(name = "full_name", nullable = false)
	private String fullName;

	/** 마지막으로 본 기본 브랜치. push 중 이 브랜치만 풀이로 본다 (S08). */
	@Column(name = "default_branch", nullable = false)
	private String defaultBranch;

	/** S08에서 쓴다. */
	@Column(name = "path_prefix")
	private @Nullable String pathPrefix;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private LinkedRepositoryStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	private LinkedRepository(long userId, long installationId, long repoId, String fullName, String defaultBranch,
			Instant now) {
		this.userId = userId;
		this.installationId = installationId;
		this.repoId = repoId;
		this.fullName = Objects.requireNonNull(fullName, "fullName");
		this.defaultBranch = Objects.requireNonNull(defaultBranch, "defaultBranch");
		this.status = LinkedRepositoryStatus.ACTIVE;
		this.createdAt = Objects.requireNonNull(now, "now");
		this.updatedAt = now;
	}

	/** 새 활성 연결. */
	public static LinkedRepository activate(long userId, long installationId, long repoId, String fullName,
			String defaultBranch, Instant now) {
		return new LinkedRepository(userId, installationId, repoId, fullName, defaultBranch, now);
	}

	/** GitHub에서 다시 본 이름·기본 브랜치로 고친다. 바뀐 게 있으면 {@code true}. */
	public boolean refresh(String newFullName, String newDefaultBranch, Instant now) {
		if (this.fullName.equals(newFullName) && this.defaultBranch.equals(newDefaultBranch)) {
			return false;
		}
		this.fullName = Objects.requireNonNull(newFullName, "newFullName");
		this.defaultBranch = Objects.requireNonNull(newDefaultBranch, "newDefaultBranch");
		this.updatedAt = Objects.requireNonNull(now, "now");
		return true;
	}

	/** 다른 레포를 골라서 이 연결을 끊는다. */
	public void deactivate(Instant now) {
		this.status = LinkedRepositoryStatus.INACTIVE;
		this.updatedAt = Objects.requireNonNull(now, "now");
	}

	public boolean isActive() {
		return this.status == LinkedRepositoryStatus.ACTIVE;
	}

}
