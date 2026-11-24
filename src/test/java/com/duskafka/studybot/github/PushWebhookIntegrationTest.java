package com.duskafka.studybot.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import com.duskafka.studybot.github.PushPayloads.Commit;
import com.duskafka.studybot.progress.SolveToAssign;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * S08 수용 기준: push 웹훅을 풀이 기록으로 (5.3 'push 처리 순서' 1~4·6, 5.4 '확인 대기', T3). 실제 push 처리기를 부른다
 * ({@link WebhookTestConfiguration.RecordingPushHandler#passThrough()}).
 *
 * <p>테스트마다 사용자·설치·연결(기본 브랜치 {@code main})을 새로 만든다. 레포 ID가 테스트마다 달라서 DB를 공유해도 서로 섞이지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class PushWebhookIntegrationTest extends WebhookTestSupport {

	private static final String BOJ_MESSAGE = "[Silver I] Title: 미로 탐색, Time: 76 ms, Memory: 14532 KB -BaekjoonHub";

	private static final String BOJ_FILE = "백준/Silver/2178. 미로 탐색/미로 탐색.java";

	private static final String BOJ_README = "백준/Silver/2178. 미로 탐색/README.md";

	private static final String PROGRAMMERS_MESSAGE = "[level 1] Title: 문자열 내 p와 y의 개수, Time: 0.03 ms, Memory: 77.8 MB -BaekjoonHub";

	private static final String PROGRAMMERS_FILE = "프로그래머스/1/12916. 문자열 내 p와 y의 개수/문자열 내 p와 y의 개수.java";

	private static final String PROGRAMMERS_README = "프로그래머스/1/12916. 문자열 내 p와 y의 개수/README.md";

	private GithubConnectionRows rows;

	private TestUser user;

	private long repoId;

	private long linkId;

	@BeforeEach
	void setUpLinkedRepository() {
		pushHandler.passThrough();
		rows = new GithubConnectionRows(jdbc);
		user = rows.newUser("pusher", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		repoId = GithubConnectionRows.newRepoId();
		linkId = rows.insertLink(user, installationId, repoId, user.login() + "/algorithm", "ACTIVE", clock.instant());
	}

	// --- T3와 풀이 기록의 값 ---

	@Test
	@DisplayName("T3: 문제 2개를 각각 커밋한 push → 풀이 기록 2개 (출처 GITHUB, CONFIRMED, 커밋 SHA, 원본 경로, 파서 버전)")
	void t3TwoCommitsWithOneProblemEachMakeTwoSolveRecords() {
		Commit boj = Commit.adding(BOJ_MESSAGE, BOJ_FILE, BOJ_README);
		Commit programmers = Commit.adding(PROGRAMMERS_MESSAGE, PROGRAMMERS_FILE, PROGRAMMERS_README);
		String delivery = newDeliveryId();

		assertThat(sendSigned("push", delivery, PushPayloads.push(repoId, PushPayloads.MAIN, List.of(boj, programmers))))
			.hasStatus(202);

		InboxRow inbox = onlyInboxRow(delivery);
		assertThat(inbox.status()).isEqualTo("PROCESSED");
		List<SolveRow> solves = solves();
		assertThat(solves).hasSize(2);
		assertThat(solves).allSatisfy(solve -> {
			assertThat(solve.userId()).isEqualTo(user.id());
			assertThat(solve.source()).isEqualTo("GITHUB");
			assertThat(solve.status()).isEqualTo("CONFIRMED");
			assertThat(solve.repoId()).isEqualTo(repoId);
			assertThat(solve.inboxId()).isEqualTo(inbox.id());
			assertThat(solve.receivedAt()).isEqualTo(inbox.receivedAt());
			assertThat(solve.parserVersion()).isEqualTo(1);
		});
		assertThat(solves.get(0)).satisfies(solve -> {
			assertThat(solve.platform()).isEqualTo("BOJ");
			assertThat(solve.problemNo()).isEqualTo("2178");
			assertThat(solve.title()).isEqualTo("미로 탐색");
			assertThat(solve.commitSha()).isEqualTo(boj.sha());
			assertThat(solve.rawPath()).isEqualTo(BOJ_FILE);
		});
		assertThat(solves.get(1)).satisfies(solve -> {
			assertThat(solve.platform()).isEqualTo("PROGRAMMERS");
			assertThat(solve.problemNo()).isEqualTo("12916");
			assertThat(solve.title()).isEqualTo("문자열 내 p와 y의 개수");
			assertThat(solve.commitSha()).isEqualTo(programmers.sha());
			assertThat(solve.rawPath()).isEqualTo(PROGRAMMERS_FILE);
		});
	}

	@Test
	@DisplayName("S08: 같은 push를 다시 받아도(다른 delivery id, 같은 커밋) 풀이 기록이 늘지 않는다 (확인 대기 포함)")
	void redeliveredPushDoesNotAddSolveRecords() {
		byte[] body = PushPayloads.push(repoId, PushPayloads.MAIN,
				List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE), Commit.adding("코드트리 풀이", "codetree/별 찍기/Main.java")));
		String first = newDeliveryId();
		String second = newDeliveryId();

		assertThat(sendSigned("push", first, body)).hasStatus(202);
		assertThat(sendSigned("push", second, body)).hasStatus(202);

		assertThat(onlyInboxRow(first).status()).isEqualTo("PROCESSED");
		assertThat(onlyInboxRow(second).status()).isEqualTo("PROCESSED");
		assertThat(solves()).extracting(SolveRow::status).containsExactly("CONFIRMED", "PENDING_IDENTIFICATION");
		assertThat(solves()).extracting(SolveRow::inboxId).containsOnly(onlyInboxRow(first).id());
		assertThat(studyDayAssignment.calls()).hasSize(1);
	}

	@Test
	@DisplayName("S08: force push로 같은 문제가 새 SHA로 오면 풀이 기록이 하나 더 생긴다 (같은 날 중복은 S09의 유니크 제약이 막음)")
	void sameProblemWithNewShaAddsSolveRecord() {
		sendSigned("push", newDeliveryId(),
				PushPayloads.push(repoId, PushPayloads.MAIN, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE))));
		sendSigned("push", newDeliveryId(),
				PushPayloads.push(repoId, PushPayloads.MAIN, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE))));

		assertThat(solves()).hasSize(2).extracting(SolveRow::problemNo).containsOnly("2178");
		assertThat(solves()).extracting(SolveRow::commitSha).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("S08: 풀이 기록의 인정 시각은 수신함의 수신 시각과 같다 (처리 시각을 일부러 3시간 뒤로)")
	void solveIsRecognizedAtReceivedTimeNotProcessingTime() {
		Instant receivedAt = Instant.parse("2026-11-20T14:59:30Z");
		clock.setInstant(receivedAt);
		pushHandler.willDo(event -> {
			clock.advance(Duration.ofHours(3));
			return pushHandler.delegate(event);
		});
		String delivery = newDeliveryId();

		sendSigned("push", delivery, PushPayloads.push(repoId, PushPayloads.MAIN, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE))));

		InboxRow inbox = onlyInboxRow(delivery);
		assertThat(inbox.receivedAt()).isEqualTo(receivedAt);
		assertThat(inbox.processedAt()).isEqualTo(receivedAt.plus(Duration.ofHours(3)));
		assertThat(solves()).singleElement().extracting(SolveRow::receivedAt).isEqualTo(receivedAt);
		assertThat(studyDayAssignment.calls()).singleElement().extracting(SolveToAssign::receivedAt).isEqualTo(receivedAt);
	}

	@Test
	@DisplayName("S08: 풀이 기록에는 payload의 커밋 작성자 이메일이 남지 않는다 (R16, 9.8)")
	void solveRecordDoesNotKeepCommitAuthorEmail() {
		byte[] body = PushPayloads.push(repoId, PushPayloads.MAIN,
				List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE), Commit.adding("코드트리 풀이", "codetree/별 찍기/Main.java")));
		assertThat(new String(body, StandardCharsets.UTF_8)).contains(PushPayloads.AUTHOR_EMAIL);

		sendSigned("push", newDeliveryId(), body);

		List<String> rowsAsJson = jdbc.sql("select row_to_json(s)::text from studybot.solve_records s where user_id = :userId")
			.param("userId", user.id())
			.query(String.class)
			.list();
		assertThat(rowsAsJson).hasSize(2).noneMatch(row -> row.contains(PushPayloads.AUTHOR_EMAIL));
	}

	// --- IGNORED ---

	@Test
	@DisplayName("S08: 기본 브랜치가 아닌 push → IGNORED, 기록 없음")
	void pushToOtherBranchIsIgnored() {
		String delivery = newDeliveryId();

		assertThat(sendSigned("push", delivery,
				PushPayloads.push(repoId, "refs/heads/feature", List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE)))))
			.hasStatus(202);

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("IGNORED");
		assertThat(solves()).isEmpty();
		assertThat(studyDayAssignment.calls()).isEmpty();
	}

	@Test
	@DisplayName("S08: 태그 push는 기본 브랜치와 이름이 같아도 → IGNORED")
	void tagPushIsIgnored() {
		String delivery = newDeliveryId();

		sendSigned("push", delivery, PushPayloads.push(repoId, "refs/tags/main", List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE))));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("IGNORED");
		assertThat(solves()).isEmpty();
	}

	@Test
	@DisplayName("S08: 브랜치 삭제 push → IGNORED, 기록 없음")
	void branchDeletionPushIsIgnored() {
		String delivery = newDeliveryId();

		assertThat(sendSigned("push", delivery, PushPayloads.branchDeleted(repoId, PushPayloads.MAIN))).hasStatus(202);

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("IGNORED");
		assertThat(solves()).isEmpty();
	}

	@Test
	@DisplayName("S08: 연결하지 않은 레포의 push → IGNORED")
	void pushToUnlinkedRepositoryIsIgnored() {
		long unlinkedRepoId = GithubConnectionRows.newRepoId();
		String delivery = newDeliveryId();

		sendSigned("push", delivery,
				PushPayloads.push(unlinkedRepoId, PushPayloads.MAIN, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE))));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("IGNORED");
		assertThat(solveCountOfRepo(unlinkedRepoId)).isZero();
	}

	@Test
	@DisplayName("S08: 활성이 아닌 연결(설치 정지 중 SUSPENDED, 끊긴 INACTIVE)의 push → IGNORED")
	void pushToInactiveLinkIsIgnored() {
		jdbc.sql("update studybot.linked_repositories set status = 'SUSPENDED' where id = :id").param("id", linkId).update();
		String suspended = newDeliveryId();
		sendSigned("push", suspended, PushPayloads.push(repoId, PushPayloads.MAIN, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE))));

		jdbc.sql("update studybot.linked_repositories set status = 'INACTIVE' where id = :id").param("id", linkId).update();
		String inactive = newDeliveryId();
		sendSigned("push", inactive, PushPayloads.push(repoId, PushPayloads.MAIN, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE))));

		assertThat(onlyInboxRow(suspended).status()).isEqualTo("IGNORED");
		assertThat(onlyInboxRow(inactive).status()).isEqualTo("IGNORED");
		assertThat(solves()).isEmpty();
	}

	// --- 확인 대기 ---

	@Test
	@DisplayName("S08: 식별 실패 + 경로 접두사 안의 파일 → PENDING_IDENTIFICATION 기록 하나 (커밋마다 하나, 원본 경로는 첫 파일)")
	void unidentifiedFilesInsidePathPrefixMakeOnePendingRecord() {
		setPathPrefix("algorithm");
		Commit commit = Commit.adding("별 찍기 풀이", "algorithm/codetree/별 찍기/Main.java",
				"algorithm/codetree/별 찍기/Helper.java");
		String delivery = newDeliveryId();

		sendSigned("push", delivery, PushPayloads.push(repoId, PushPayloads.MAIN, List.of(commit)));

		InboxRow inbox = onlyInboxRow(delivery);
		assertThat(inbox.status()).isEqualTo("PROCESSED");
		assertThat(solves()).singleElement().satisfies(solve -> {
			assertThat(solve.status()).isEqualTo("PENDING_IDENTIFICATION");
			assertThat(solve.source()).isEqualTo("GITHUB");
			assertThat(solve.platform()).isNull();
			assertThat(solve.problemNo()).isNull();
			assertThat(solve.title()).isNull();
			assertThat(solve.commitSha()).isEqualTo(commit.sha());
			assertThat(solve.rawPath()).isEqualTo("algorithm/codetree/별 찍기/Main.java");
			assertThat(solve.receivedAt()).isEqualTo(inbox.receivedAt());
			assertThat(solve.inboxId()).isEqualTo(inbox.id());
			assertThat(solve.parserVersion()).isEqualTo(1);
		});
		assertThat(studyDayAssignment.calls()).isEmpty();
	}

	@Test
	@DisplayName("S08: 식별 실패 + 경로 접두사 밖의 파일 → 기록 없음 (접두사는 경로 조각 단위로 본다)")
	void unidentifiedFileOutsidePathPrefixIsIgnored() {
		setPathPrefix("algorithm");
		String delivery = newDeliveryId();

		sendSigned("push", delivery, PushPayloads.push(repoId, PushPayloads.MAIN,
				List.of(Commit.adding("메모", "notes/Main.java"), Commit.adding("다른 폴더", "algorithms/Main.java"))));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		assertThat(solves()).isEmpty();
	}

	@Test
	@DisplayName("S08: 경로 접두사가 비어 있으면 레포 전체가 범위다 (사람이 정함, 2026-11-24 \"레포 전체로 봄\")")
	void emptyPathPrefixMeansWholeRepository() {
		String delivery = newDeliveryId();

		sendSigned("push", delivery, PushPayloads.push(repoId, PushPayloads.MAIN, List.of(Commit.adding("풀이", "Main.java"))));

		assertThat(solves()).singleElement().satisfies(solve -> {
			assertThat(solve.status()).isEqualTo("PENDING_IDENTIFICATION");
			assertThat(solve.rawPath()).isEqualTo("Main.java");
		});
	}

	@Test
	@DisplayName("S08: 문제를 식별한 커밋의 나머지 풀이 파일은 확인 대기로 남기지 않는다 (확인 대기는 커밋마다)")
	void commitWithIdentifiedProblemLeavesNoPendingRecord() {
		sendSigned("push", newDeliveryId(), PushPayloads.push(repoId, PushPayloads.MAIN,
				List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE, "util/FastReader.java"))));

		assertThat(solves()).singleElement().extracting(SolveRow::status).isEqualTo("CONFIRMED");
	}

	@Test
	@DisplayName("S08: README만 고친 커밋 → 기록 없음")
	void readmeOnlyCommitMakesNoRecord() {
		String delivery = newDeliveryId();

		sendSigned("push", delivery,
				PushPayloads.push(repoId, PushPayloads.MAIN, List.of(Commit.modifying("README 고침", "README.md", BOJ_README))));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		assertThat(solves()).isEmpty();
	}

	// --- 배정 연결 지점 ---

	@Test
	@DisplayName("S08: 배정 연결 지점은 새로 생긴 CONFIRMED 풀이마다 한 번 불린다 (확인 대기에는 불리지 않음)")
	void assignmentIsCalledForNewConfirmedSolvesOnly() {
		Instant receivedAt = Instant.parse("2026-11-21T03:00:00Z");
		clock.setInstant(receivedAt);

		sendSigned("push", newDeliveryId(), PushPayloads.push(repoId, PushPayloads.MAIN,
				List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE), Commit.adding("코드트리 풀이", "codetree/별 찍기/Main.java"))));

		SolveRow confirmed = solves().getFirst();
		assertThat(studyDayAssignment.calls())
			.containsExactly(new SolveToAssign(confirmed.id(), user.id(), "BOJ", "2178", receivedAt));
	}

	@Test
	@DisplayName("S08: 배정 연결 지점이 실패하면 풀이 기록도 롤백되고 수신함은 FAILED다 (같은 트랜잭션)")
	void assignmentFailureRollsBackSolveRecords() {
		studyDayAssignment.willThrow(new IllegalStateException("테스트: 배정 실패"));
		String delivery = newDeliveryId();

		assertThat(sendSigned("push", delivery,
				PushPayloads.push(repoId, PushPayloads.MAIN, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE)))))
			.hasStatus(202);

		InboxRow inbox = onlyInboxRow(delivery);
		assertThat(inbox.status()).isEqualTo("FAILED");
		assertThat(inbox.lastError()).isEqualTo("java.lang.IllegalStateException");
		assertThat(solves()).isEmpty();
	}

	// --- 그 밖의 모양 ---

	@Test
	@DisplayName("S08: 커밋 목록이 비었는데 before와 after가 다르면 경고 로그만 남긴다 (compare API 보완은 P2)")
	void emptyCommitListWithMovedHeadIsLogged(CapturedOutput output) {
		String delivery = newDeliveryId();

		sendSigned("push", delivery, PushPayloads.withoutCommits(repoId, PushPayloads.MAIN));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		assertThat(solves()).isEmpty();
		assertThat(output.getOut()).contains("WARN").contains("delivery=" + delivery);
	}

	@Test
	@DisplayName("S08: repository가 없는 push는 처리에 실패한다 (FAILED, 오류 설명은 개인정보 없는 문장)")
	void pushWithoutRepositoryFails() {
		String delivery = newDeliveryId();

		sendSigned("push", delivery, PushPayloads.withoutRepository(PushPayloads.MAIN, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE))));

		InboxRow inbox = onlyInboxRow(delivery);
		assertThat(inbox.status()).isEqualTo("FAILED");
		assertThat(inbox.lastError()).startsWith("com.duskafka.studybot.github.webhook.WebhookHandlingException: ");
	}

	private void setPathPrefix(@Nullable String prefix) {
		jdbc.sql("update studybot.linked_repositories set path_prefix = :prefix where id = :id")
			.param("prefix", prefix)
			.param("id", linkId)
			.update();
	}

	private long solveCountOfRepo(long repo) {
		return jdbc.sql("select count(*) from studybot.solve_records where repo_id = :repoId")
			.param("repoId", repo)
			.query(Long.class)
			.single();
	}

	/** 이 테스트 사용자의 풀이 기록 (넣은 순서). */
	private List<SolveRow> solves() {
		return jdbc.sql("""
				select id, user_id, source, platform, problem_no, title, received_at, commit_sha, repo_id, inbox_id, raw_path,
					parser_version, status
				from studybot.solve_records where user_id = :userId order by id
				""")
			.param("userId", user.id())
			.query((rs, rowNum) -> new SolveRow(rs.getLong("id"), rs.getLong("user_id"), rs.getString("source"),
					rs.getString("platform"), rs.getString("problem_no"), rs.getString("title"),
					rs.getObject("received_at", OffsetDateTime.class).toInstant(), rs.getString("commit_sha"),
					(Long) rs.getObject("repo_id"), (Long) rs.getObject("inbox_id"), rs.getString("raw_path"),
					(Integer) rs.getObject("parser_version"), rs.getString("status")))
			.list();
	}

	record SolveRow(long id, long userId, String source, @Nullable String platform, @Nullable String problemNo,
			@Nullable String title, Instant receivedAt, @Nullable String commitSha, @Nullable Long repoId,
			@Nullable Long inboxId, @Nullable String rawPath, @Nullable Integer parserVersion, String status) {
	}

}
