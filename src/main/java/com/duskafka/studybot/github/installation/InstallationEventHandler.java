package com.duskafka.studybot.github.installation;

import com.duskafka.studybot.github.installation.InstallationPayloads.Account;
import com.duskafka.studybot.github.installation.InstallationPayloads.InstallationEvent;
import com.duskafka.studybot.github.webhook.GithubEvent;
import com.duskafka.studybot.github.webhook.GithubEventHandler;
import com.duskafka.studybot.github.webhook.GithubWebhookEvent;
import com.duskafka.studybot.github.webhook.HandlingResult;
import com.duskafka.studybot.github.webhook.WebhookHandlingException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code installation} 웹훅 처리기 (S07 만들 것 1·2). created, deleted, suspend, unsuspend를 다루고 나머지 action
 * (예: new_permissions_accepted)은 IGNORED로 둔다. GitHub API를 부르지 않는다.
 */
@Component
@RequiredArgsConstructor
class InstallationEventHandler implements GithubEventHandler {

	private final JsonMapper jsonMapper;

	private final GithubInstallationLinker linker;

	@Override
	public GithubEvent event() {
		return GithubEvent.INSTALLATION;
	}

	@Override
	public HandlingResult handle(GithubWebhookEvent event) {
		InstallationEvent payload = InstallationPayloads.read(this.jsonMapper, event.payload(), InstallationEvent.class);
		String action = payload.action() != null ? payload.action() : "";
		return switch (action) {
			case "created" -> created(payload);
			case "deleted" -> this.linker.deleted(InstallationPayloads.installationId(payload.installation()));
			case "suspend" -> this.linker.suspended(InstallationPayloads.installationId(payload.installation()));
			case "unsuspend" -> this.linker.unsuspended(InstallationPayloads.installationId(payload.installation()));
			default -> HandlingResult.IGNORED;
		};
	}

	private HandlingResult created(InstallationEvent payload) {
		long installationId = InstallationPayloads.installationId(payload.installation());
		Account account = payload.installation().account();
		if (account == null || account.id() == null || isBlank(account.login()) || isBlank(account.type())) {
			throw new WebhookHandlingException("설치 웹훅에 installation.account가 없어요");
		}
		// sender가 없으면 설치자를 알 수 없으니 계정과 다른 값으로 두어 연결하지 않는다 (ghost 사용자도 계정과 다르다).
		Account sender = payload.sender();
		long senderId = sender != null && sender.id() != null ? sender.id() : -1L;
		return this.linker.created(installationId, account.id(), account.login(), account.type(), senderId);
	}

	private static boolean isBlank(@Nullable String value) {
		return value == null || value.isBlank();
	}

}
