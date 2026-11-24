package com.duskafka.studybot.github;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * S07 테스트가 DB를 직접 준비하고 읽는 도우미. 사용자·설치·연결 행을 넣고 읽는다.
 *
 * <p>테스트끼리 DB를 공유하므로 GitHub ID·설치 ID·레포 ID는 JVM 안에서 겹치지 않게 새로 받는다
 * (S04·S05 테스트, S06 payload 샘플의 값과 겹치지 않는 범위).
 */
public final class GithubConnectionRows {

	private static final AtomicLong NEXT_GITHUB_ID = new AtomicLong(7_000_000L);

	private static final AtomicLong NEXT_INSTALLATION_ID = new AtomicLong(8_000_000L);

	private static final AtomicLong NEXT_REPO_ID = new AtomicLong(9_000_000L);

	private final JdbcClient jdbc;

	public GithubConnectionRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public static long newGithubId() {
		return NEXT_GITHUB_ID.incrementAndGet();
	}

	public static long newInstallationId() {
		return NEXT_INSTALLATION_ID.incrementAndGet();
	}

	public static long newRepoId() {
		return NEXT_REPO_ID.incrementAndGet();
	}

	/** 사용자를 DB에 넣는다 (가입한 사람). */
	public TestUser newUser(String login, Instant createdAt) {
		long githubId = newGithubId();
		long id = jdbc.sql("""
				insert into studybot.users (github_id, github_login, created_at)
				values (:githubId, :login, :createdAt) returning id
				""")
			.param("githubId", githubId)
			.param("login", login + "-" + githubId)
			.param("createdAt", Timestamp.from(createdAt))
			.query(Long.class)
			.single();
		return new TestUser(id, githubId, login + "-" + githubId);
	}

	/** 설치 행을 바로 넣는다 (웹훅을 거치지 않고 상태를 준비할 때). 설치자가 계정 주인인지는 {@code senderId == accountId}로 정한다. */
	public void insertInstallation(long installationId, @Nullable Long userId, long accountId, String accountType,
			long senderId, String status, Instant createdAt) {
		jdbc.sql("""
				insert into studybot.github_installations
					(installation_id, user_id, account_id, account_login, account_type, installed_by_owner, status, created_at,
					updated_at)
				values (:installationId, :userId, :accountId, :login, :accountType, :byOwner, :status, :createdAt, :createdAt)
				""")
			.param("installationId", installationId)
			.param("userId", userId)
			.param("accountId", accountId)
			.param("login", "account-" + accountId)
			.param("accountType", accountType)
			.param("byOwner", senderId == accountId)
			.param("status", status)
			.param("createdAt", Timestamp.from(createdAt))
			.update();
	}

	/** 이 사용자의 개인 계정 설치(연결됨, ACTIVE)를 넣고 설치 ID를 돌려준다. */
	public long insertLinkedInstallation(TestUser user, Instant createdAt) {
		long installationId = newInstallationId();
		insertInstallation(installationId, user.id(), user.githubId(), "User", user.githubId(), "ACTIVE", createdAt);
		return installationId;
	}

	/** 연결 행을 바로 넣고 ID를 돌려준다. */
	public long insertLink(TestUser user, long installationId, long repoId, String fullName, String status,
			Instant createdAt) {
		return jdbc.sql("""
				insert into studybot.linked_repositories
					(user_id, installation_id, repo_id, full_name, default_branch, status, created_at, updated_at)
				values (:userId, :installationId, :repoId, :fullName, 'main', :status, :createdAt, :createdAt)
				returning id
				""")
			.param("userId", user.id())
			.param("installationId", installationId)
			.param("repoId", repoId)
			.param("fullName", fullName)
			.param("status", status)
			.param("createdAt", Timestamp.from(createdAt))
			.query(Long.class)
			.single();
	}

	public Optional<InstallationRow> installation(long installationId) {
		return jdbc.sql("""
				select installation_id, user_id, account_id, account_login, account_type, installed_by_owner, status,
					repositories_revision
				from studybot.github_installations where installation_id = :id
				""")
			.param("id", installationId)
			.query((rs, rowNum) -> new InstallationRow(rs.getLong("installation_id"),
					(Long) rs.getObject("user_id"), (Long) rs.getObject("account_id"), rs.getString("account_login"),
					rs.getString("account_type"), (Boolean) rs.getObject("installed_by_owner"), rs.getString("status"),
					rs.getLong("repositories_revision")))
			.optional();
	}

	public InstallationRow existingInstallation(long installationId) {
		return installation(installationId)
			.orElseThrow(() -> new AssertionError("설치 행이 없어요: " + installationId));
	}

	public LinkRow link(long linkId) {
		return jdbc.sql("""
				select id, user_id, installation_id, repo_id, full_name, default_branch, status
				from studybot.linked_repositories where id = :id
				""")
			.param("id", linkId)
			.query(GithubConnectionRows::linkRow)
			.single();
	}

	/** 이 사용자의 연결 행 전부 (오래된 것부터). */
	public List<LinkRow> linksOf(TestUser user) {
		return jdbc.sql("""
				select id, user_id, installation_id, repo_id, full_name, default_branch, status
				from studybot.linked_repositories where user_id = :userId order by id
				""")
			.param("userId", user.id())
			.query(GithubConnectionRows::linkRow)
			.list();
	}

	private static LinkRow linkRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new LinkRow(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("installation_id"), rs.getLong("repo_id"),
				rs.getString("full_name"), rs.getString("default_branch"), rs.getString("status"));
	}

	/**
	 * 테스트용 사용자.
	 * @param id {@code users.id}
	 * @param githubId {@code users.github_id}
	 * @param login {@code users.github_login}
	 */
	public record TestUser(long id, long githubId, String login) {
	}

	/**
	 * 설치 행. 계정 값과 설치자 여부는 created를 아직 못 본 설치(처음 보는 설치의 deleted·suspend·unsuspend로 남긴 행)면 NULL이다.
	 */
	public record InstallationRow(long installationId, @Nullable Long userId, @Nullable Long accountId,
			@Nullable String accountLogin, @Nullable String accountType, @Nullable Boolean installedByOwner, String status,
			long repositoriesRevision) {
	}

	public record LinkRow(long id, long userId, long installationId, long repoId, String fullName, String defaultBranch,
			String status) {
	}

}
