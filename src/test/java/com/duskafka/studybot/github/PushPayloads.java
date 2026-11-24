package com.duskafka.studybot.github;

import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * S08 push 테스트가 보내는 본문. S06 샘플({@code github-payloads/push.json})을 읽어 ref·삭제 여부·레포 ID·before·after·커밋만
 * 바꾼다. 커밋의 나머지 모양(작성자·커미터와 그 이메일 등)은 샘플의 커밋 그대로다 (출처는 그 폴더의 README).
 */
final class PushPayloads {

	static final String MAIN = "refs/heads/main";

	/** 샘플 커밋의 작성자 이메일 (가짜 값). 풀이 기록에 남지 않는지 볼 때 쓴다. */
	static final String AUTHOR_EMAIL = "tester@example.com";

	private static final String ZERO_SHA = "0".repeat(40);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private PushPayloads() {
	}

	/** 커밋 하나. {@code added}·{@code modified}만 정하고 {@code removed}는 비운다. */
	record Commit(String sha, String message, List<String> added, List<String> modified) {

		/** 파일을 더한 커밋. SHA는 새로 만든다. */
		static Commit adding(String message, String... added) {
			return new Commit(newSha(), message, List.of(added), List.of());
		}

		/** 파일을 고친 커밋. SHA는 새로 만든다. */
		static Commit modifying(String message, String... modified) {
			return new Commit(newSha(), message, List.of(), List.of(modified));
		}

	}

	static String newSha() {
		byte[] bytes = new byte[20];
		ThreadLocalRandom.current().nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	/** 커밋들을 올린 push. before는 새 SHA, after는 마지막 커밋이다. */
	static byte[] push(long repoId, String ref, List<Commit> commits) {
		String after = commits.isEmpty() ? newSha() : commits.getLast().sha();
		return build(repoId, ref, false, newSha(), after, commits);
	}

	/** 브랜치를 지운 push: {@code deleted: true}, after는 0으로 채운 SHA, 커밋 없음. */
	static byte[] branchDeleted(long repoId, String ref) {
		return build(repoId, ref, true, newSha(), ZERO_SHA, List.of());
	}

	/** 커밋 목록이 빈 push. before와 after가 다르다 (목록이 잘렸을 때의 모양, 5.3 '주의할 것'). */
	static byte[] withoutCommits(long repoId, String ref) {
		return build(repoId, ref, false, newSha(), newSha(), List.of());
	}

	/** {@code repository}가 없는 push (모양이 틀린 본문). */
	static byte[] withoutRepository(String ref, List<Commit> commits) {
		ObjectNode root = (ObjectNode) JSON.readTree(push(1L, ref, commits));
		root.remove("repository");
		return JSON.writeValueAsBytes(root);
	}

	private static byte[] build(long repoId, String ref, boolean deleted, String before, String after,
			List<Commit> commits) {
		ObjectNode root = (ObjectNode) JSON.readTree(WebhookTestSupport.payload("push.json"));
		ObjectNode template = (ObjectNode) root.get("commits").get(0);
		root.put("ref", ref);
		root.put("deleted", deleted);
		root.put("created", false);
		root.put("before", before);
		root.put("after", after);
		((ObjectNode) root.get("repository")).put("id", repoId);
		ArrayNode commitNodes = JSON.createArrayNode();
		for (Commit commit : commits) {
			ObjectNode node = template.deepCopy();
			node.put("id", commit.sha());
			node.put("tree_id", newSha());
			node.put("message", commit.message());
			node.set("added", strings(commit.added()));
			node.set("modified", strings(commit.modified()));
			node.set("removed", JSON.createArrayNode());
			commitNodes.add(node);
		}
		root.set("commits", commitNodes);
		if (commitNodes.isEmpty()) {
			root.putNull("head_commit");
		}
		else {
			root.set("head_commit", commitNodes.get(commitNodes.size() - 1).deepCopy());
		}
		return JSON.writeValueAsBytes(root);
	}

	private static ArrayNode strings(List<String> values) {
		ArrayNode array = JSON.createArrayNode();
		values.forEach(array::add);
		return array;
	}

}
