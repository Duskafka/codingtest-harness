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
import org.jspecify.annotations.Nullable;

/**
 * GitHub App 설치 (data-model.md 4.3 {@code github_installations}, 5.2). 글로서리 권장 이름은 {@code GitHubInstallation}이지만
 * S06의 {@code Github*} 이름과 맞췄다 (S07 PR 판단 기록).
 *
 * <p>행은 {@link GithubInstallationRepository#insertIfAbsent}가 한 문장으로 만든다 (같은 설치 이벤트가 겹쳐 와도 한 행만 남게).
 * {@code userId}는 서명된 웹훅의 설치자 정보로만 채운다 ({@link GithubInstallationRepository#claim}). Setup URL의 값으로는 채우지 않는다 [S37].
 */
@Entity
@Table(name = "github_installations")
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

	/** 설치한 계정의 GitHub ID ({@code installation.account.id}). */
	@Column(name = "account_id", nullable = false, updatable = false)
	private Long accountId;

	@Column(name = "account_login", nullable = false)
	private String accountLogin;

	/** GitHub의 계정 종류 ({@code User}, {@code Organization} 등) 그대로. */
	@Column(name = "account_type", nullable = false, updatable = false)
	private String accountType;

	/** 설치 이벤트를 일으킨 사람의 GitHub ID ({@code sender.id}). */
	@Column(name = "sender_id", nullable = false, updatable = false)
	private Long senderId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private InstallationStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** 정지한다. 삭제된 설치는 바꾸지 않는다. 바꿨으면 {@code true}. */
	public boolean suspend(Instant at) {
		return changeStatus(InstallationStatus.ACTIVE, InstallationStatus.SUSPENDED, at);
	}

	/** 정지를 푼다. 삭제된 설치는 바꾸지 않는다. 바꿨으면 {@code true}. */
	public boolean unsuspend(Instant at) {
		return changeStatus(InstallationStatus.SUSPENDED, InstallationStatus.ACTIVE, at);
	}

	/** 삭제됐다. 되돌리지 않는다. */
	public void markDeleted(Instant at) {
		if (this.status != InstallationStatus.DELETED) {
			this.status = InstallationStatus.DELETED;
			this.updatedAt = Objects.requireNonNull(at, "at");
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
