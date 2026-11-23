package com.duskafka.studybot.github.connection;

import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.github.api.InstalledRepository;
import com.duskafka.studybot.github.connection.ConnectionViews.InstallationView;
import com.duskafka.studybot.github.connection.ConnectionViews.LinkView;
import com.duskafka.studybot.github.persistence.GithubInstallationRepository;
import com.duskafka.studybot.github.persistence.InstallationStatus;
import com.duskafka.studybot.github.persistence.LinkedRepository;
import com.duskafka.studybot.github.persistence.LinkedRepositoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연결 화면의 DB 작업. GitHub API는 트랜잭션 밖에서 부르고, 그 결과를 여기서 짧은 트랜잭션으로 반영한다
 * (외부 호출 동안 DB 트랜잭션을 잡고 있지 않게).
 *
 * <p>연결을 저장할 때는 설치 행을 잠그고 아직 이 사용자의 ACTIVE 설치이고 레포 목록 리비전이 GitHub를 부르기 전과 같은지
 * 다시 본다. GitHub를 부르는 사이에 suspend·deleted·removed 웹훅이 오면, 웹훅이 먼저 끝났으면 저장하지 않고, 저장이 먼저
 * 끝났으면 웹훅이 기다렸다가 새 연결까지 함께 끈다 (PR #14 리뷰 반영).
 */
@Component
@RequiredArgsConstructor
class GithubConnectionStore {

	private final GithubInstallationRepository installations;

	private final LinkedRepositoryRepository links;

	private final Clock clock;

	@Transactional(readOnly = true)
	Optional<InstallationView> currentInstallation(long userId) {
		return this.installations.findCurrentByUserId(userId)
			.map(installation -> new InstallationView(installation.getInstallationId(), installation.getAccountLogin(),
					installation.getStatus(), installation.getRepositoriesRevision()));
	}

	@Transactional(readOnly = true)
	Optional<LinkView> currentLink(long userId) {
		return this.links.findCurrentByUserId(userId).map(LinkView::of);
	}

	/**
	 * 이 레포를 사용자의 풀이 레포로 연결한다. 이미 같은 레포가 활성으로 연결돼 있으면 이름·기본 브랜치만 고친다 (멱등).
	 * 다른 레포가 연결돼 있으면 그 연결을 끊고 새로 연결한다 (사용자당 하나).
	 *
	 * @throws org.springframework.dao.DataIntegrityViolationException 같은 레포가 다른 사용자에게 활성으로 연결돼 있음, 또는 동시에 연결함
	 */
	@Transactional
	LinkView link(long userId, long installationId, long expectedRevision, InstalledRepository repository) {
		if (!lockActiveInstallation(userId, installationId, expectedRevision)) {
			throw new ConflictException("GitHub App 설치나 설치한 레포가 바뀌었어요. 다시 시도해 주세요.");
		}
		Instant now = this.clock.instant();
		Optional<LinkedRepository> current = this.links.findCurrentByUserId(userId);
		if (current.isPresent() && current.get().isActive() && current.get().getRepoId() == repository.id()
				&& current.get().getInstallationId() == installationId) {
			current.get().refresh(repository.fullName(), repository.defaultBranch(), now);
			return LinkView.of(current.get());
		}
		if (current.isPresent()) {
			current.get().deactivate(now);
			// Hibernate는 INSERT를 UPDATE보다 먼저 보낸다. 부분 UNIQUE(사용자당 하나)에 걸리지 않게 끊는 것부터 반영한다.
			this.links.flush();
		}
		LinkedRepository created = this.links.saveAndFlush(LinkedRepository.activate(userId, installationId,
				repository.id(), repository.fullName(), repository.defaultBranch(), now));
		return LinkView.of(created);
	}

	/**
	 * 연결이 없을 때만 이 레포를 연결한다 (레포가 하나뿐인 설치의 자동 연결). 이미 연결이 있으면 그것을 돌려주고,
	 * 그사이 설치가 정지·삭제됐거나 레포가 빠졌으면 연결하지 않는다.
	 */
	@Transactional
	Optional<LinkView> linkIfNone(long userId, long installationId, long expectedRevision,
			InstalledRepository repository) {
		if (!lockActiveInstallation(userId, installationId, expectedRevision)) {
			return this.links.findCurrentByUserId(userId).map(LinkView::of);
		}
		Optional<LinkedRepository> current = this.links.findCurrentByUserId(userId);
		if (current.isPresent()) {
			return current.map(LinkView::of);
		}
		return Optional.of(LinkView.of(this.links.saveAndFlush(LinkedRepository.activate(userId, installationId,
				repository.id(), repository.fullName(), repository.defaultBranch(), this.clock.instant()))));
	}

	/** 연결한 레포의 이름·기본 브랜치가 GitHub에서 바뀌었으면 고친다 (repo_id로 같은 레포). */
	@Transactional
	Optional<LinkView> refresh(long userId, InstalledRepository repository) {
		return this.links.findCurrentByUserId(userId)
			.filter(link -> link.getRepoId() == repository.id())
			.map(link -> {
				link.refresh(repository.fullName(), repository.defaultBranch(), this.clock.instant());
				return LinkView.of(link);
			});
	}

	/**
	 * 설치의 레포 목록 전체를 봤는데 연결한 레포가 없다('모든 레포'→'고른 레포'로 바꾼 뒤 등): 그 연결을 끊고 지금 연결을 돌려준다
	 * (사람이 정함, 2026-11-23 "목록이 한 페이지로 다 보일 때만 끊음").
	 */
	@Transactional
	Optional<LinkView> deactivateMissing(long userId, long repoId) {
		this.links.deactivateActive(userId, repoId, this.clock.instant());
		return this.links.findCurrentByUserId(userId).map(LinkView::of);
	}

	/** 설치 행을 잠그고, 아직 이 사용자의 ACTIVE 설치이며 레포 목록 리비전이 그대로인지 본다. */
	private boolean lockActiveInstallation(long userId, long installationId, long expectedRevision) {
		return this.installations.findForUpdate(installationId)
			.filter(installation -> installation.getStatus() == InstallationStatus.ACTIVE
					&& Objects.equals(installation.getUserId(), userId)
					&& installation.getRepositoriesRevision() == expectedRevision)
			.isPresent();
	}

}
