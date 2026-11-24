package com.duskafka.studybot.github.push;

import com.duskafka.studybot.github.persistence.LinkedRepository;
import com.duskafka.studybot.github.persistence.LinkedRepositoryRepository;
import com.duskafka.studybot.github.webhook.GithubEvent;
import com.duskafka.studybot.github.webhook.GithubEventHandler;
import com.duskafka.studybot.github.webhook.GithubWebhookEvent;
import com.duskafka.studybot.github.webhook.HandlingResult;
import com.duskafka.studybot.github.webhook.WebhookHandlingException;
import com.duskafka.studybot.verification.GithubCommit;
import com.duskafka.studybot.verification.GithubPush;
import com.duskafka.studybot.verification.GithubSolveRecorder;
import com.duskafka.studybot.verification.RecordedSolves;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code push} 웹훅 처리기 (5.3 'push 처리 순서', S08 만들 것 1). GitHub API를 부르지 않는다 (10초 응답 제한 [S33]).
 *
 * <ol>
 * <li>ref를 지운 push면 IGNORED.</li>
 * <li>{@code repository.id}로 활성 연결 레포를 찾는다. 없으면 IGNORED (설치는 했지만 고르지 않은 레포, 정지·끊긴 연결).</li>
 * <li>ref가 연결 레포의 기본 브랜치({@code refs/heads/} + {@code default_branch})가 아니면 IGNORED. 기본 브랜치는 연결 레포에
 * 남은 값으로 본다 (스펙 그대로, PR 열린 질문).</li>
 * <li>커밋마다 SHA·메시지·추가·수정 경로만 꺼내 verification에 넘긴다 ({@link GithubSolveRecorder}). 풀이 기록이 하나도 안 생겨도
 * (README만 고침, 다시 받은 push) 처리한 것이라 PROCESSED다.</li>
 * </ol>
 *
 * 커밋 목록이 비었는데 {@code before}와 {@code after}가 다르면 목록이 잘렸을 수 있다. compare API로 보완하는 건 P2라 지금은 경고
 * 로그만 남긴다 (S08 '만들지 않을 것'). 로그에는 전달 ID·수신함 ID·레포 ID·건수만 남기고 경로·메시지는 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PushEventHandler implements GithubEventHandler {

	private static final String BRANCH_REF_PREFIX = "refs/heads/";

	private final JsonMapper jsonMapper;

	private final LinkedRepositoryRepository linkedRepositories;

	private final GithubSolveRecorder solveRecorder;

	@Override
	public GithubEvent event() {
		return GithubEvent.PUSH;
	}

	@Override
	public HandlingResult handle(GithubWebhookEvent event) {
		PushPayload payload = read(event);
		if (Boolean.TRUE.equals(payload.deleted())) {
			return HandlingResult.IGNORED;
		}
		long repoId = repoId(payload);
		Optional<LinkedRepository> found = linkedRepositories.findActiveByRepoId(repoId);
		if (found.isEmpty()) {
			return HandlingResult.IGNORED;
		}
		LinkedRepository repo = found.get();
		if (!(BRANCH_REF_PREFIX + repo.getDefaultBranch()).equals(payload.ref())) {
			return HandlingResult.IGNORED;
		}
		List<GithubCommit> commits = commits(payload);
		if (commits.isEmpty() && !Objects.equals(payload.before(), payload.after())) {
			log.warn("push의 커밋 목록이 비었는데 before와 after가 달라요. compare API 보완은 P2예요: delivery={}, inbox={}, repo={}",
					event.deliveryId(), event.inboxId(), repoId);
		}
		RecordedSolves recorded = solveRecorder.record(new GithubPush(repo.getUserId(), repoId, event.inboxId(),
				event.receivedAt(), repo.getPathPrefix(), commits));
		log.info("push를 풀이 기록으로 남겼어요: inbox={}, repo={}, commits={}, confirmed={}, pending={}", event.inboxId(),
				repoId, commits.size(), recorded.confirmed(), recorded.pending());
		return HandlingResult.PROCESSED;
	}

	private PushPayload read(GithubWebhookEvent event) {
		try {
			return jsonMapper.treeToValue(event.payload(), PushPayload.class);
		}
		catch (JacksonException ex) {
			// 메시지에 payload 값이 섞일 수 있어 원인은 붙이지 않는다 (last_error·로그에 개인정보를 남기지 않게).
			throw new WebhookHandlingException("push 웹훅 본문의 모양이 예상과 달라요");
		}
	}

	private static long repoId(PushPayload payload) {
		if (payload.repository() == null || payload.repository().id() == null) {
			throw new WebhookHandlingException("push 웹훅에 repository.id가 없어요");
		}
		return payload.repository().id();
	}

	private static List<GithubCommit> commits(PushPayload payload) {
		if (payload.commits() == null) {
			return List.of();
		}
		List<GithubCommit> commits = new ArrayList<>(payload.commits().size());
		for (PushPayload.Commit commit : payload.commits()) {
			if (commit == null || commit.id() == null || commit.id().isBlank()) {
				throw new WebhookHandlingException("push 웹훅의 커밋에 id가 없어요");
			}
			commits.add(new GithubCommit(commit.id(), commit.message() != null ? commit.message() : "",
					changedPaths(commit)));
		}
		return commits;
	}

	/** 더한 파일 다음 고친 파일. 같은 경로는 한 번만. 지운 파일은 풀이가 아니라서 넣지 않는다 (5.3 'push 처리 순서' 3). */
	private static List<String> changedPaths(PushPayload.Commit commit) {
		Set<String> paths = new LinkedHashSet<>();
		addAll(paths, commit.added());
		addAll(paths, commit.modified());
		return List.copyOf(paths);
	}

	private static void addAll(Set<String> paths, @Nullable List<String> values) {
		if (values == null) {
			return;
		}
		for (String value : values) {
			if (value != null && !value.isEmpty()) {
				paths.add(value);
			}
		}
	}

}
