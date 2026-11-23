package com.duskafka.studybot.github.connection;

import com.duskafka.studybot.github.api.InstalledRepository;
import com.duskafka.studybot.github.connection.ConnectionViews.InstallationView;
import com.duskafka.studybot.github.connection.ConnectionViews.LinkView;
import com.duskafka.studybot.github.persistence.GithubInstallationRepository;
import com.duskafka.studybot.github.persistence.LinkedRepository;
import com.duskafka.studybot.github.persistence.LinkedRepositoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연결 화면의 DB 작업. GitHub API는 트랜잭션 밖에서 부르고, 그 결과를 여기서 짧은 트랜잭션으로 반영한다
 * (외부 호출 동안 DB 트랜잭션을 잡고 있지 않게).
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
					installation.getStatus()));
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
	LinkView link(long userId, long installationId, InstalledRepository repository) {
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

	/** 연결이 없을 때만 이 레포를 연결한다 (레포가 하나뿐인 설치의 자동 연결). 이미 연결이 있으면 그것을 돌려준다. */
	@Transactional
	LinkView linkIfNone(long userId, long installationId, InstalledRepository repository) {
		Optional<LinkedRepository> current = this.links.findCurrentByUserId(userId);
		if (current.isPresent()) {
			return LinkView.of(current.get());
		}
		return LinkView.of(this.links.saveAndFlush(LinkedRepository.activate(userId, installationId, repository.id(),
				repository.fullName(), repository.defaultBranch(), this.clock.instant())));
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

}
