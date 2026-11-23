package com.duskafka.studybot.github;

import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * S07 웹훅 테스트가 보내는 본문. S06 샘플({@code github-payloads/installation.json}, {@code installation_repositories.json})을
 * 읽어 action·설치·계정·보낸 사람·레포만 바꾼다. 나머지 모양은 샘플 그대로다 (출처는 그 폴더의 README).
 */
final class InstallationPayloads {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private InstallationPayloads() {
	}

	/** 계정. {@code type}은 GitHub 값({@code User}, {@code Organization}). */
	record Account(long id, String login, String type) {

		static Account user(long id) {
			return new Account(id, "user-" + id, "User");
		}

		static Account organization(long id) {
			return new Account(id, "org-" + id, "Organization");
		}

	}

	/** 레포. 이름은 {@code owner/name}. */
	record Repo(long id, String fullName) {
	}

	/** {@code installation} 이벤트 본문. */
	static byte[] installation(String action, long installationId, Account account, Account sender) {
		ObjectNode root = (ObjectNode) JSON.readTree(WebhookTestSupport.payload("installation.json"));
		root.put("action", action);
		ObjectNode installation = (ObjectNode) root.get("installation");
		installation.put("id", installationId);
		installation.put("target_id", account.id());
		installation.put("target_type", account.type());
		setAccount((ObjectNode) installation.get("account"), account);
		setAccount((ObjectNode) root.get("sender"), sender);
		return JSON.writeValueAsBytes(root);
	}

	/** {@code installation_repositories} 이벤트 본문. */
	static byte[] installationRepositories(String action, long installationId, Account account, Account sender,
			List<Repo> added, List<Repo> removed) {
		ObjectNode root = (ObjectNode) JSON.readTree(WebhookTestSupport.payload("installation_repositories.json"));
		root.put("action", action);
		ObjectNode installation = (ObjectNode) root.get("installation");
		installation.put("id", installationId);
		setAccount((ObjectNode) installation.get("account"), account);
		setAccount((ObjectNode) root.get("sender"), sender);
		root.set("repositories_added", repos(added));
		root.set("repositories_removed", repos(removed));
		return JSON.writeValueAsBytes(root);
	}

	private static void setAccount(ObjectNode node, Account account) {
		node.put("id", account.id());
		node.put("login", account.login());
		node.put("type", account.type());
	}

	private static ArrayNode repos(List<Repo> repos) {
		ArrayNode array = JSON.createArrayNode();
		for (Repo repo : repos) {
			ObjectNode node = array.addObject();
			node.put("id", repo.id());
			node.put("node_id", "FAKE_NODE_ID");
			node.put("name", repo.fullName().substring(repo.fullName().indexOf('/') + 1));
			node.put("full_name", repo.fullName());
			node.put("private", false);
		}
		return array;
	}

}
