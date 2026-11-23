package com.duskafka.studybot.github.installation;

import com.duskafka.studybot.github.webhook.WebhookHandlingException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code installation}·{@code installation_repositories} 웹훅에서 쓰는 값만 담은 모양
 * (<a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#installation">GitHub 문서</a>).
 * 나머지 필드는 읽지 않는다.
 */
final class InstallationPayloads {

	private InstallationPayloads() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Account(@Nullable Long id, @Nullable String login, @Nullable String type) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Installation(@Nullable Long id, @Nullable Account account) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Repository(@Nullable Long id) {
	}

	/** {@code installation} 이벤트. {@code action}: created, deleted, suspend, unsuspend, new_permissions_accepted. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record InstallationEvent(@Nullable String action, @Nullable Installation installation, @Nullable Account sender) {
	}

	/** {@code installation_repositories} 이벤트. {@code action}: added, removed. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record InstallationRepositoriesEvent(@Nullable String action, @Nullable Installation installation,
			@JsonProperty("repositories_removed") @Nullable List<Repository> repositoriesRemoved) {
	}

	static <T> T read(JsonMapper jsonMapper, JsonNode payload, Class<T> type) {
		try {
			return jsonMapper.treeToValue(payload, type);
		}
		catch (JacksonException ex) {
			// 메시지에 payload 값이 섞일 수 있어 원인은 붙이지 않는다 (last_error·로그에 개인정보를 남기지 않게).
			throw new WebhookHandlingException("설치 웹훅 본문의 모양이 예상과 달라요");
		}
	}

	/** 설치 ID. 없으면 처리할 수 없다. */
	static long installationId(@Nullable Installation installation) {
		if (installation == null || installation.id() == null) {
			throw new WebhookHandlingException("설치 웹훅에 installation.id가 없어요");
		}
		return installation.id();
	}

}
