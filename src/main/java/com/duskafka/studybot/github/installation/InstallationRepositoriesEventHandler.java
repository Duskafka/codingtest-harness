package com.duskafka.studybot.github.installation;

import com.duskafka.studybot.github.installation.InstallationPayloads.InstallationRepositoriesEvent;
import com.duskafka.studybot.github.installation.InstallationPayloads.Repository;
import com.duskafka.studybot.github.webhook.GithubEvent;
import com.duskafka.studybot.github.webhook.GithubEventHandler;
import com.duskafka.studybot.github.webhook.GithubWebhookEvent;
import com.duskafka.studybot.github.webhook.HandlingResult;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code installation_repositories} 웹훅 처리기 (S07 만들 것 3). removed면 그 레포의 연결을 끊는다 (알림은 S13).
 * added는 바꿀 것이 없다. 설치의 레포 목록은 필요할 때 GitHub API로 본다 ({@code GET /api/me/github/repositories}).
 *
 * <p>GitHub 문서: 설치 범위를 '모든 레포'에서 '고른 레포'로 바꾸면 {@code repositories_removed}가 비어서 온다. 그때 빠진 레포는
 * 여기서 알 수 없다 (S07 PR '그 밖에 발견한 것').
 */
@Component
@RequiredArgsConstructor
class InstallationRepositoriesEventHandler implements GithubEventHandler {

	private final JsonMapper jsonMapper;

	private final GithubInstallationLinker linker;

	@Override
	public GithubEvent event() {
		return GithubEvent.INSTALLATION_REPOSITORIES;
	}

	@Override
	public HandlingResult handle(GithubWebhookEvent event) {
		InstallationRepositoriesEvent payload = InstallationPayloads.read(this.jsonMapper, event.payload(),
				InstallationRepositoriesEvent.class);
		if (!"removed".equals(payload.action())) {
			return HandlingResult.IGNORED;
		}
		long installationId = InstallationPayloads.installationId(payload.installation());
		List<Repository> removed = payload.repositoriesRemoved() != null ? payload.repositoriesRemoved() : List.of();
		List<Long> repoIds = removed.stream().map(Repository::id).filter(Objects::nonNull).toList();
		return this.linker.repositoriesRemoved(installationId, repoIds);
	}

}
