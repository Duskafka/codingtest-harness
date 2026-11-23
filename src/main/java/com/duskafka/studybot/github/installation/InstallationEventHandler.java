package com.duskafka.studybot.github.installation;

import com.duskafka.studybot.github.installation.GithubInstallationLinker.InstallationAccount;
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
 * (예: new_permissions_accepted)은 IGNORED로 둔다. GitHub API를 부르지 않는다. 전달 순서가 바뀌어도 맞게 하려고 모든 action에
 * payload의 계정 값을 넘긴다 ({@link GithubInstallationLinker}).
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
			case "created" -> this.linker.created(InstallationPayloads.installationId(payload.installation()),
					requireAccount(payload));
			case "deleted" -> this.linker.deleted(InstallationPayloads.installationId(payload.installation()),
					accountOf(payload));
			case "suspend" -> this.linker.suspended(InstallationPayloads.installationId(payload.installation()),
					accountOf(payload));
			case "unsuspend" -> this.linker.unsuspended(InstallationPayloads.installationId(payload.installation()),
					accountOf(payload));
			default -> HandlingResult.IGNORED;
		};
	}

	private static InstallationAccount requireAccount(InstallationEvent payload) {
		InstallationAccount account = accountOf(payload);
		if (account == null) {
			throw new WebhookHandlingException("설치 웹훅에 installation.account가 없어요");
		}
		return account;
	}

	/**
	 * payload의 계정 값. 계정 주인이 직접 설치했는지만 남기고 설치자의 GitHub ID는 남기지 않는다. sender가 없으면 설치자를 알 수 없어
	 * 주인이 설치하지 않은 것으로 본다 (ghost 사용자도 계정과 다르다). 계정 값이 모자라면 비운다.
	 */
	private static @Nullable InstallationAccount accountOf(InstallationEvent payload) {
		Account account = payload.installation() != null ? payload.installation().account() : null;
		if (account == null || account.id() == null || isBlank(account.login()) || isBlank(account.type())) {
			return null;
		}
		Account sender = payload.sender();
		boolean installedByOwner = sender != null && account.id().equals(sender.id());
		return new InstallationAccount(account.id(), account.login(), account.type(), installedByOwner);
	}

	private static boolean isBlank(@Nullable String value) {
		return value == null || value.isBlank();
	}

}
