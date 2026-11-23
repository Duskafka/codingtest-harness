package com.duskafka.studybot.github.connection;

import com.duskafka.studybot.github.persistence.InstallationStatus;
import com.duskafka.studybot.github.persistence.LinkedRepository;
import com.duskafka.studybot.github.persistence.LinkedRepositoryStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;

/** 연결 화면에 보여 줄 값. 엔티티 대신 이 값들을 웹 계층에 넘긴다. */
public final class ConnectionViews {

	private ConnectionViews() {
	}

	/** 내 설치. */
	public record InstallationView(long installationId, String accountLogin, InstallationStatus status) {
	}

	/** 내 풀이 레포 연결. */
	public record LinkView(long repoId, String fullName, String defaultBranch, LinkedRepositoryStatus status) {

		static LinkView of(LinkedRepository link) {
			return new LinkView(link.getRepoId(), link.getFullName(), link.getDefaultBranch(), link.getStatus());
		}

	}

	/** 설치에서 고를 수 있는 레포. {@code linked}면 지금 연결한 레포다. */
	public record RepositoryChoice(long repoId, String fullName, String defaultBranch, boolean isPrivate,
			boolean linked) {
	}

	/** 레포 목록 화면. 설치가 없으면 {@code installation}이 비고, 설치가 ACTIVE가 아니면 목록이 빈다. */
	public record RepositoriesView(@Nullable InstallationView installation, @Nullable LinkView linkedRepository,
			Page<RepositoryChoice> repositories) {
	}

}
