package com.duskafka.studybot.github.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * GitHub App 설치 (data-model.md 4.3 {@code github_installations}, 5.2). 글로서리 권장 이름은 {@code GitHubInstallation}이지만
 * S06의 {@code Github*} 이름과 맞췄다 (S07 PR 판단 기록).
 *
 * <p>행은 {@link GithubInstallationRepository#recordCreated}·{@link GithubInstallationRepository#recordStateIfAbsent}가 한 문장으로
 * 만든다 (같은 설치 이벤트가 겹쳐 와도 한 행만 남게). 계정 값과 설치자 여부는 created만 채운다. created보다 먼저 온 deleted·suspend·
 * unsuspend로 남긴 행은 created가 올 때까지 비어 있다 (PR #14 2차 리뷰 반영).
 * {@code userId}는 서명된 웹훅의 설치자 정보로만 채운다 ({@link GithubInstallationRepository#claim}). Setup URL의 값으로는 채우지 않는다 [S37].
 *
 * <p>바뀐 컬럼만 UPDATE한다({@code @DynamicUpdate}). {@code user_id}·리비전은 일괄 UPDATE로 바뀌므로, 엔티티로 상태를 바꿀 때
 * 읽어 둔 옛 값으로 덮어쓰지 않게 하기 위해서다 (PR #14 리뷰 반영).
 */
@Entity
@Table(name = "github_installations")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubInstallation {

	/** GitHub의 설치 ID. */
	@Id
	@Column(name = "installation_id")
	private Long installationId;

	/** 연결된 사용자. 아직 가입하지 않았거나, 개인 계정 설치가 아니거나, 설치자가 계정 주인이 아니면 비어 있다. */
	@Column(name = "user_id")
	private @Nullable Long userId;

	/** 설치한 계정의 GitHub ID ({@code installation.account.id}). created를 아직 못 봤으면 비어 있다. */
	@Column(name = "account_id", updatable = false)
	private @Nullable Long accountId;

	/** created를 아직 못 봤으면 비어 있다. 사용자에게 이은 설치에는 늘 있다 (DB CHECK). */
	@Column(name = "account_login", updatable = false)
	private @Nullable String accountLogin;

	/** GitHub의 계정 종류 ({@code User}, {@code Organization} 등) 그대로. created를 아직 못 봤으면 비어 있다. */
	@Column(name = "account_type", updatable = false)
	private @Nullable String accountType;

	/**
	 * 계정 주인이 직접 설치했는지 ({@code installation} created의 {@code account.id == sender.id}). 설치자의 GitHub ID는 남기지 않는다
	 * (조직 설치나 남이 대신 설치하면 회원이 아닌 사람의 ID라서, security.md 9.8 최소 수집). created를 아직 못 봤으면 비어 있다.
	 */
	@Column(name = "installed_by_owner", updatable = false)
	private @Nullable Boolean installedByOwner;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private InstallationStatus status;

	/**
	 * 설치의 레포 목록 리비전. 레포가 빠질 때마다 오른다({@link GithubInstallationRepository#bumpRepositoriesRevision}).
	 * GitHub에서 레포 목록을 읽은 뒤 연결을 저장할 때 그사이 빠진 레포가 없는지 이 값으로 본다.
	 */
	@Column(name = "repositories_revision", nullable = false, insertable = false, updatable = false)
	private long repositoriesRevision;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * DELETED가 된 시각. DELETED일 때만, 그리고 늘 있다 (V6 CHECK). 삭제 뒤 30일이 지나면 계정 값을 지우는 정리 작업(security.md 9.8, S15)의
	 * 기준이다.
	 */
	@Column(name = "deleted_at", insertable = false)
	private @Nullable Instant deletedAt;

	/** 정지한다. 삭제된 설치는 바꾸지 않는다. 바꿨으면 {@code true}. */
	public boolean suspend(Instant at) {
		return changeStatus(InstallationStatus.ACTIVE, InstallationStatus.SUSPENDED, at);
	}

	/** 정지를 푼다. 삭제된 설치는 바꾸지 않는다. 바꿨으면 {@code true}. */
	public boolean unsuspend(Instant at) {
		return changeStatus(InstallationStatus.SUSPENDED, InstallationStatus.ACTIVE, at);
	}

	/** 삭제됐다. 되돌리지 않는다. 삭제 시각을 남긴다. */
	public void markDeleted(Instant at) {
		if (this.status != InstallationStatus.DELETED) {
			this.status = InstallationStatus.DELETED;
			this.updatedAt = Objects.requireNonNull(at, "at");
			this.deletedAt = at;
		}
	}

	public boolean isDeleted() {
		return this.status == InstallationStatus.DELETED;
	}

	private boolean changeStatus(InstallationStatus from, InstallationStatus to, Instant at) {
		if (this.status != from) {
			return false;
		}
		this.status = to;
		this.updatedAt = Objects.requireNonNull(at, "at");
		return true;
	}

}
