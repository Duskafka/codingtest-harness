package com.duskafka.studybot.verification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.duskafka.studybot.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * S08 {@code solve_records} 제약 (V7). 앱 코드를 거치지 않고 SQL로 넣어서 DB가 막는지 본다.
 * 확인 대기는 문제가 비어 있고, 문제를 식별한 기록은 문제가 있다. GitHub 기록은 커밋·레포·수신함·원본 경로·파서 버전이 있다.
 */
@IntegrationTest
class SolveRecordSchemaIntegrationTest {

	private static final AtomicLong NEXT_GITHUB_ID = new AtomicLong(7_500_000L);

	private static final Instant AT = Instant.parse("2026-11-20T00:00:00Z");

	@Autowired
	private JdbcClient jdbc;

	private long userId;

	private long inboxId;

	@BeforeEach
	void setUp() {
		long githubId = NEXT_GITHUB_ID.incrementAndGet();
		userId = jdbc.sql("""
				insert into studybot.users (github_id, github_login, created_at)
				values (:githubId, :login, :at) returning id
				""")
			.param("githubId", githubId)
			.param("login", "schema-" + githubId)
			.param("at", Timestamp.from(AT))
			.query(Long.class)
			.single();
		inboxId = jdbc.sql("""
				insert into studybot.webhook_inbox (source, delivery_id, event_type, payload, received_at, status, attempts)
				values ('GITHUB', :delivery, 'push', '{}'::jsonb, :at, 'PROCESSED', 1) returning id
				""")
			.param("delivery", UUID.randomUUID().toString())
			.param("at", Timestamp.from(AT))
			.query(Long.class)
			.single();
	}

	@Test
	@DisplayName("S08: 문제를 식별한 GitHub 기록과 확인 대기 기록은 넣을 수 있다")
	void validRecordsAreAccepted() {
		assertThatCode(() -> insertGithub("CONFIRMED", "BOJ", "1000", "a".repeat(40), "백준/Bronze V/1000. A+B/A+B.java", 1))
			.doesNotThrowAnyException();
		assertThatCode(() -> insertGithub("PENDING_IDENTIFICATION", null, null, "b".repeat(40), "Main.java", 1))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("S08: CONFIRMED는 문제(플랫폼·번호)가 있어야 하고, 확인 대기는 문제가 없어야 한다")
	void statusAndProblemMustAgree() {
		assertThatThrownBy(() -> insertGithub("CONFIRMED", null, null, "c".repeat(40), "Main.java", 1))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertGithub("PENDING_IDENTIFICATION", "BOJ", "1000", "d".repeat(40), "Main.java", 1))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("S08: 플랫폼과 번호는 함께 있고, 번호는 0으로 시작하지 않는 숫자다 (ProblemRef 정규형)")
	void problemIsCanonical() {
		assertThatThrownBy(() -> insertGithub("CONFIRMED", "BOJ", "0001", "e".repeat(40), "Main.java", 1))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertGithub("CONFIRMED", "UNKNOWN", "1", "f".repeat(40), "Main.java", 1))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("S08: GitHub 기록은 커밋 SHA·원본 경로·파서 버전이 있어야 한다")
	void githubRecordNeedsCommitPathAndParserVersion() {
		assertThatThrownBy(() -> insertGithub("CONFIRMED", "BOJ", "1000", null, "Main.java", 1))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertGithub("CONFIRMED", "BOJ", "1000", "1".repeat(40), null, 1))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertGithub("CONFIRMED", "BOJ", "1000", "2".repeat(40), "Main.java", null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("S08: 같은 사용자·커밋에서 같은 문제, 같은 원본 경로는 한 번만 남는다")
	void sameCommitProblemOrPathIsUnique() {
		insertGithub("CONFIRMED", "BOJ", "1000", "3".repeat(40), "a/Main.java", 1);
		insertGithub("PENDING_IDENTIFICATION", null, null, "4".repeat(40), "b/Main.java", 1);

		assertThatThrownBy(() -> insertGithub("CONFIRMED", "BOJ", "1000", "3".repeat(40), "a/Other.java", 1))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertGithub("PENDING_IDENTIFICATION", null, null, "4".repeat(40), "b/Main.java", 1))
			.isInstanceOf(DataIntegrityViolationException.class);
		// 확인 대기에 문제를 지정해(S10) CONFIRMED가 된 뒤에도 같은 경로의 확인 대기를 다시 넣지 못한다.
		jdbc.sql("""
				update studybot.solve_records set status = 'CONFIRMED', platform = 'BOJ', problem_no = '2000'
				where user_id = :userId and commit_sha = :sha
				""").param("userId", userId).param("sha", "4".repeat(40)).update();
		assertThatThrownBy(() -> insertGithub("PENDING_IDENTIFICATION", null, null, "4".repeat(40), "b/Main.java", 1))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void insertGithub(String status, @Nullable String platform, @Nullable String problemNo,
			@Nullable String commitSha, @Nullable String rawPath, @Nullable Integer parserVersion) {
		jdbc.sql("""
				insert into studybot.solve_records
					(user_id, source, platform, problem_no, title, received_at, commit_sha, repo_id, inbox_id, raw_path,
					parser_version, status)
				values (:userId, 'GITHUB', :platform, :problemNo, null, :at, :sha, 200000001, :inboxId, :rawPath,
					:parserVersion, :status)
				""")
			.param("userId", userId)
			.param("platform", platform)
			.param("problemNo", problemNo)
			.param("at", Timestamp.from(AT))
			.param("sha", commitSha)
			.param("inboxId", inboxId)
			.param("rawPath", rawPath)
			.param("parserVersion", parserVersion)
			.param("status", status)
			.update();
	}

}
