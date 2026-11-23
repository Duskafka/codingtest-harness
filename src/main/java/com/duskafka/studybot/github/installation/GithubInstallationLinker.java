package com.duskafka.studybot.github.installation;

import com.duskafka.studybot.auth.UserDirectory;
import com.duskafka.studybot.github.persistence.GithubInstallation;
import com.duskafka.studybot.github.persistence.GithubInstallationRepository;
import com.duskafka.studybot.github.persistence.InstallationStatus;
import com.duskafka.studybot.github.persistence.LinkedRepositoryRepository;
import com.duskafka.studybot.github.webhook.HandlingResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설치와 사용자·레포 연결의 상태를 바꾼다 (5.2 처리 순서 1·2·4, S07 만들 것 1·2·3).
 *
 * <p>사용자 연결은 서명된 {@code installation} 웹훅이 남긴 설치자 정보로만 한다. 개인 계정({@code User})이고
 * {@code account.id == sender.id}일 때만 그 GitHub ID의 사용자에게 잇는다. Setup URL의 {@code installation_id}는 쓰지 않는다 [S37].
 * 웹훅 처리기는 GitHub API를 부르지 않는다 (10초 응답 제한 [S33]).
 *
 * <p>같은 설치의 웹훅이 겹치거나 순서가 바뀌어 와도 상태를 덮어쓰지 않게 한다 (PR #14 리뷰 반영).
 * <ul>
 * <li>상태를 바꾸는 처리는 설치 행을 잠그고 읽는다. 먼저 시작한 처리가 커밋할 때까지 뒤의 처리가 기다린다.</li>
 * <li>GitHub는 전달 순서를 보장하지 않는다. 처음 보는 설치의 deleted·suspend·unsuspend는 payload의 계정 값으로 그 상태의 행을 남긴다.
 * 늦게 온 created는 이미 있는 행을 바꾸지 않으므로 삭제·정지가 되살아나지 않는다.</li>
 * <li>레포가 빠지면 연결이 없어도 설치의 레포 목록 리비전을 올린다. 그사이 GitHub에서 읽은 레포 목록으로 연결을 저장하려던 요청은
 * 리비전이 달라 저장하지 않는다 ({@code GithubConnectionStore}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GithubInstallationLinker {

	/**
	 * 설치 웹훅 payload의 계정 값. 처음 보는 설치를 남길 때 쓴다.
	 *
	 * @param installedByOwner 계정 주인이 직접 설치했는지 ({@code account.id == sender.id})
	 */
	record InstallationAccount(long accountId, String accountLogin, String accountType, boolean installedByOwner) {
	}

	/** GitHub의 개인 계정 종류 값. */
	static final String USER_ACCOUNT = "User";

	private final GithubInstallationRepository installations;

	private final LinkedRepositoryRepository links;

	private final UserDirectory users;

	private final Clock clock;

	/**
	 * {@code installation} created: 설치를 남기고, 연결 조건을 넘으면 이미 가입한 사용자에게 잇는다. 아직 가입하지 않았으면 비워 두고
	 * 가입할 때 {@link #claim}이 잇는다. 이미 있는 설치(같은 이벤트가 다시 왔거나, 삭제·정지가 먼저 왔음)는 상태를 바꾸지 않는다.
	 */
	@Transactional
	HandlingResult created(long installationId, InstallationAccount account) {
		Instant now = this.clock.instant();
		this.installations.insertIfAbsent(installationId, account.accountId(), account.accountLogin(),
				account.accountType(), account.installedByOwner(), InstallationStatus.ACTIVE.name(), now);
		linkIfEligible(account, now);
		return HandlingResult.PROCESSED;
	}

	/**
	 * {@code installation} deleted: 설치를 DELETED로, 그 설치의 연결을 모두 끊는다. 처음 보는 설치면 삭제 이력으로 남긴다.
	 * 계정 값도 없는 처음 보는 설치면 IGNORED.
	 */
	@Transactional
	HandlingResult deleted(long installationId, @Nullable InstallationAccount account) {
		Instant now = this.clock.instant();
		Optional<GithubInstallation> found = this.installations.findForUpdate(installationId);
		if (found.isEmpty()) {
			if (record(installationId, account, InstallationStatus.DELETED, now)) {
				return HandlingResult.PROCESSED;
			}
			found = this.installations.findForUpdate(installationId);
			if (found.isEmpty()) {
				return HandlingResult.IGNORED;
			}
		}
		found.get().markDeleted(now);
		this.links.deactivateInstallation(installationId, now);
		return HandlingResult.PROCESSED;
	}

	/** {@code installation} suspend: 설치와 활성 연결을 SUSPENDED로. 처음 보는 설치면 정지 상태로 남긴다. 삭제된 설치면 IGNORED. */
	@Transactional
	HandlingResult suspended(long installationId, @Nullable InstallationAccount account) {
		Instant now = this.clock.instant();
		Optional<GithubInstallation> found = this.installations.findForUpdate(installationId);
		if (found.isEmpty()) {
			if (record(installationId, account, InstallationStatus.SUSPENDED, now)) {
				return HandlingResult.PROCESSED;
			}
			found = this.installations.findForUpdate(installationId);
		}
		if (found.isEmpty() || found.get().isDeleted()) {
			return HandlingResult.IGNORED;
		}
		found.get().suspend(now);
		this.links.suspendInstallation(installationId, now);
		return HandlingResult.PROCESSED;
	}

	/**
	 * {@code installation} unsuspend: 설치를 ACTIVE로, 정지로 꺼진 연결만 다시 ACTIVE로. 처음 보는 설치면 활성 상태로 남긴다.
	 * 삭제된 설치면 IGNORED.
	 */
	@Transactional
	HandlingResult unsuspended(long installationId, @Nullable InstallationAccount account) {
		Instant now = this.clock.instant();
		Optional<GithubInstallation> found = this.installations.findForUpdate(installationId);
		if (found.isEmpty()) {
			if (record(installationId, account, InstallationStatus.ACTIVE, now)) {
				return HandlingResult.PROCESSED;
			}
			found = this.installations.findForUpdate(installationId);
		}
		if (found.isEmpty() || found.get().isDeleted()) {
			return HandlingResult.IGNORED;
		}
		found.get().unsuspend(now);
		this.links.unsuspendInstallation(installationId, now);
		return HandlingResult.PROCESSED;
	}

	/**
	 * {@code installation_repositories} removed: 설치의 레포 목록 리비전을 올리고, 빠진 레포의 연결을 끊는다. 빠진 레포 목록이 비어
	 * 있어도('모든 레포'→'고른 레포') 리비전은 올린다. 모르는 설치면 IGNORED.
	 */
	@Transactional
	HandlingResult repositoriesRemoved(long installationId, Collection<Long> repoIds) {
		Instant now = this.clock.instant();
		if (this.installations.bumpRepositoriesRevision(installationId, now) == 0) {
			return HandlingResult.IGNORED;
		}
		if (!repoIds.isEmpty()) {
			this.links.deactivateRepositories(installationId, repoIds, now);
		}
		return HandlingResult.PROCESSED;
	}

	/**
	 * 이 사용자의 GitHub ID로 남아 있는, 주인 없는 설치를 잇는다 (가입하기 전에 설치한 경우, 5.2 처리 순서 2).
	 * 연결 조건은 웹훅 때와 같다. 로그인한 뒤(다른 스레드)와 연결 화면을 볼 때 부른다. 여러 번 불러도 결과가 같다.
	 *
	 * @return 이은 설치 수
	 */
	@Transactional
	public int claim(long userId, long githubId) {
		return this.installations.claim(userId, githubId, this.clock.instant());
	}

	/** 처음 보는 설치를 이 상태로 남긴다. 남겼으면 {@code true}. 계정 값이 없거나 그새 다른 처리가 만들었으면 {@code false}. */
	private boolean record(long installationId, @Nullable InstallationAccount account, InstallationStatus status,
			Instant now) {
		if (account == null) {
			return false;
		}
		boolean inserted = this.installations.insertIfAbsent(installationId, account.accountId(), account.accountLogin(),
				account.accountType(), account.installedByOwner(), status.name(), now) == 1;
		if (inserted) {
			linkIfEligible(account, now);
		}
		return inserted;
	}

	/** 연결 조건(개인 계정, 계정 주인이 설치)을 넘으면 그 GitHub ID로 가입한 사용자에게 잇는다. 삭제된 설치는 잇지 않는다. */
	private void linkIfEligible(InstallationAccount account, Instant now) {
		if (USER_ACCOUNT.equals(account.accountType()) && account.installedByOwner()) {
			this.users.findUserIdByGithubId(account.accountId())
				.ifPresent(userId -> this.installations.claim(userId, account.accountId(), now));
		}
	}

}
