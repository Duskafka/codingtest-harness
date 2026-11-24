package com.duskafka.studybot.github.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.github.GithubConnectionRows;
import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import com.duskafka.studybot.github.api.InstalledRepository;
import com.duskafka.studybot.github.persistence.LinkedRepository;
import com.duskafka.studybot.github.persistence.LinkedRepositoryRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 연결을 저장할 때 설치를 다시 확인하는지 본다. 서비스는 설치가 ACTIVE인 것과 레포 목록 리비전을 본 뒤 트랜잭션 밖에서 GitHub를
 * 부르고 저장한다. 그사이 suspend·deleted·removed 웹훅이 먼저 끝났으면 저장하지 않아야 한다 (정지된 설치나 빠진 레포에
 * 활성 연결이 생기지 않게). 경합 자체는 시간에 기대야 해서, 여기서는 '저장 직전에 이미 바뀐 설치'로 그 확인만 본다.
 */
@IntegrationTest
class GithubConnectionStoreIntegrationTest {

	@Autowired
	GithubConnectionStore store;

	@Autowired
	LinkedRepositoryRepository links;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	MutableClock clock;

	private GithubConnectionRows rows;

	@BeforeEach
	void setUpRows() {
		rows = new GithubConnectionRows(jdbc);
	}

	@Test
	@DisplayName("S07: GitHub를 부르는 사이 설치가 정지됐으면 풀이 레포를 연결하지 않는다 (409)")
	void linkRechecksInstallationBeforeSaving() {
		TestUser user = rows.newUser("raced", clock.instant());
		long installationId = suspendedInstallationOf(user);
		InstalledRepository repository = repositoryOf(user);

		assertThatThrownBy(() -> store.link(user.id(), installationId, 0, repository))
			.isInstanceOf(ConflictException.class);
		assertThat(store.linkIfNone(user.id(), installationId, 0, repository)).isEmpty();
		assertThat(rows.linksOf(user)).isEmpty();
	}

	@Test
	@DisplayName("S07: GitHub를 부르는 사이 설치에서 레포가 빠졌으면(리비전이 오름) 연결하지 않는다 (CodeRabbit 리뷰)")
	void linkRechecksRepositoriesRevision() {
		TestUser user = rows.newUser("revision", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		InstalledRepository repository = repositoryOf(user);
		jdbc.sql("update studybot.github_installations set repositories_revision = repositories_revision + 1 "
				+ "where installation_id = :id")
			.param("id", installationId)
			.update();

		assertThatThrownBy(() -> store.link(user.id(), installationId, 0, repository))
			.isInstanceOf(ConflictException.class);
		assertThat(store.linkIfNone(user.id(), installationId, 0, repository)).isEmpty();
		assertThat(rows.linksOf(user)).isEmpty();

		assertThat(store.link(user.id(), installationId, 1, repository).repoId()).as("리비전이 같으면 연결한다")
			.isEqualTo(repository.id());
	}

	@Test
	@DisplayName("T19: 남의 설치로는 연결을 저장하지 않는다")
	void linkRequiresOwnInstallation() {
		TestUser owner = rows.newUser("store-owner", clock.instant());
		long installationId = rows.insertLinkedInstallation(owner, clock.instant());
		TestUser other = rows.newUser("store-other", clock.instant());

		assertThatThrownBy(() -> store.link(other.id(), installationId, 0, repositoryOf(owner)))
			.isInstanceOf(ConflictException.class);
		assertThat(rows.linksOf(other)).isEmpty();
	}

	@Test
	@DisplayName("S07: 이름을 고치는 사이 다른 처리가 연결을 끊었으면 끊긴 상태를 되살리지 않는다 (CodeRabbit 리뷰)")
	void refreshDoesNotOverwriteConcurrentDeactivation() {
		TestUser user = rows.newUser("refresh", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long linkId = rows.insertLink(user, installationId, GithubConnectionRows.newRepoId(), user.login() + "/old",
				"ACTIVE", clock.instant());

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			LinkedRepository link = links.findById(linkId).orElseThrow();
			deactivateFromAnotherConnection(linkId);
			link.refresh(user.login() + "/new", "main", clock.instant());
		});

		assertThat(rows.link(linkId).status()).isEqualTo("INACTIVE");
		assertThat(rows.link(linkId).fullName()).isEqualTo(user.login() + "/new");
	}

	/** 다른 트랜잭션(웹훅 처리라고 보면 된다)이 연결을 끊고 커밋한다. */
	private void deactivateFromAnotherConnection(long linkId) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement update = connection
					.prepareStatement("update studybot.linked_repositories set status = 'INACTIVE' where id = ?")) {
			connection.setAutoCommit(true);
			update.setLong(1, linkId);
			assertThat(update.executeUpdate()).isEqualTo(1);
		}
		catch (SQLException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static InstalledRepository repositoryOf(TestUser user) {
		return new InstalledRepository(GithubConnectionRows.newRepoId(), user.login() + "/algo", "main", false);
	}

	private long suspendedInstallationOf(TestUser user) {
		long installationId = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(installationId, user.id(), user.githubId(), "User", user.githubId(), "SUSPENDED",
				clock.instant());
		return installationId;
	}

}
