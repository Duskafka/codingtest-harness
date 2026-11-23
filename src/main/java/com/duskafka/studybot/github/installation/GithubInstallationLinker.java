package com.duskafka.studybot.github.installation;

import com.duskafka.studybot.auth.UserDirectory;
import com.duskafka.studybot.github.persistence.GithubInstallation;
import com.duskafka.studybot.github.persistence.GithubInstallationRepository;
import com.duskafka.studybot.github.persistence.LinkedRepositoryRepository;
import com.duskafka.studybot.github.webhook.HandlingResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설치와 사용자·레포 연결의 상태를 바꾼다 (5.2 처리 순서 1·2·4, S07 만들 것 1·2·3).
 *
 * <p>사용자 연결은 서명된 {@code installation} 웹훅이 남긴 설치자 정보로만 한다. 개인 계정({@code User})이고
 * {@code account.id == sender.id}일 때만 그 GitHub ID의 사용자에게 잇는다. Setup URL의 {@code installation_id}는 쓰지 않는다 [S37].
 * 웹훅 처리기는 GitHub API를 부르지 않는다 (10초 응답 제한 [S33]).
 */
@Service
@RequiredArgsConstructor
public class GithubInstallationLinker {

	/** GitHub의 개인 계정 종류 값. */
	static final String USER_ACCOUNT = "User";

	private final GithubInstallationRepository installations;

	private final LinkedRepositoryRepository links;

	private final UserDirectory users;

	private final Clock clock;

	/**
	 * {@code installation} created: 설치를 남기고, 연결 조건을 넘으면 이미 가입한 사용자에게 잇는다. 아직 가입하지 않았으면 비워 두고
	 * 가입할 때 {@link #claim}이 잇는다. 같은 이벤트가 다시 와도 결과가 같다.
	 */
	@Transactional
	HandlingResult created(long installationId, long accountId, String accountLogin, String accountType,
			boolean installedByOwner) {
		Instant now = this.clock.instant();
		this.installations.insertIfAbsent(installationId, accountId, accountLogin, accountType, installedByOwner, now);
		if (USER_ACCOUNT.equals(accountType) && installedByOwner) {
			this.users.findUserIdByGithubId(accountId).ifPresent(userId -> this.installations.claim(userId, accountId, now));
		}
		return HandlingResult.PROCESSED;
	}

	/** {@code installation} deleted: 설치를 DELETED로, 그 설치의 연결을 모두 끊는다. 모르는 설치면 IGNORED. */
	@Transactional
	HandlingResult deleted(long installationId) {
		Optional<GithubInstallation> found = this.installations.findById(installationId);
		if (found.isEmpty()) {
			return HandlingResult.IGNORED;
		}
		Instant now = this.clock.instant();
		found.get().markDeleted(now);
		this.links.deactivateInstallation(installationId, now);
		return HandlingResult.PROCESSED;
	}

	/** {@code installation} suspend: 설치와 활성 연결을 SUSPENDED로. 모르거나 삭제된 설치면 IGNORED. */
	@Transactional
	HandlingResult suspended(long installationId) {
		Optional<GithubInstallation> found = this.installations.findById(installationId);
		if (found.isEmpty() || found.get().isDeleted()) {
			return HandlingResult.IGNORED;
		}
		Instant now = this.clock.instant();
		found.get().suspend(now);
		this.links.suspendInstallation(installationId, now);
		return HandlingResult.PROCESSED;
	}

	/** {@code installation} unsuspend: 설치를 ACTIVE로, 정지로 꺼진 연결만 다시 ACTIVE로. 모르거나 삭제된 설치면 IGNORED. */
	@Transactional
	HandlingResult unsuspended(long installationId) {
		Optional<GithubInstallation> found = this.installations.findById(installationId);
		if (found.isEmpty() || found.get().isDeleted()) {
			return HandlingResult.IGNORED;
		}
		Instant now = this.clock.instant();
		found.get().unsuspend(now);
		this.links.unsuspendInstallation(installationId, now);
		return HandlingResult.PROCESSED;
	}

	/** {@code installation_repositories} removed: 그 설치에서 빠진 레포의 연결을 끊는다. 끊은 게 없으면 IGNORED. */
	@Transactional
	HandlingResult repositoriesRemoved(long installationId, Collection<Long> repoIds) {
		if (repoIds.isEmpty()) {
			return HandlingResult.IGNORED;
		}
		int changed = this.links.deactivateRepositories(installationId, repoIds, this.clock.instant());
		return changed > 0 ? HandlingResult.PROCESSED : HandlingResult.IGNORED;
	}

	/**
	 * 이 사용자의 GitHub ID로 남아 있는, 주인 없는 설치를 잇는다 (가입하기 전에 설치한 경우, 5.2 처리 순서 2).
	 * 연결 조건은 웹훅 때와 같다. 로그인할 때(커밋 뒤)와 연결 화면을 볼 때 부른다. 여러 번 불러도 결과가 같다.
	 *
	 * @return 이은 설치 수
	 */
	@Transactional
	public int claim(long userId, long githubId) {
		return this.installations.claim(userId, githubId, this.clock.instant());
	}

}
