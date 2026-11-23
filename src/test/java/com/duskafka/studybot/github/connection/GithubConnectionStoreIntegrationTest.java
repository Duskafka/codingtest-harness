package com.duskafka.studybot.github.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.github.GithubConnectionRows;
import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import com.duskafka.studybot.github.api.InstalledRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 연결을 저장할 때 설치를 다시 확인하는지 본다. 서비스는 설치가 ACTIVE인 것을 본 뒤 트랜잭션 밖에서 GitHub를 부르고 저장한다.
 * 그사이 suspend·deleted 웹훅이 먼저 끝났으면 저장하지 않아야 한다 (정지된 설치에 활성 연결이 생기지 않게).
 * 경합 자체는 시간에 기대야 해서, 여기서는 '저장 직전에 이미 정지된 설치'로 그 확인만 본다.
 */
@IntegrationTest
class GithubConnectionStoreIntegrationTest {

	@Autowired
	GithubConnectionStore store;

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
		InstalledRepository repository = new InstalledRepository(GithubConnectionRows.newRepoId(), user.login() + "/algo",
				"main", false);

		assertThatThrownBy(() -> store.link(user.id(), installationId, repository))
			.isInstanceOf(ConflictException.class);
		assertThat(store.linkIfNone(user.id(), installationId, repository)).isEmpty();
		assertThat(rows.linksOf(user)).isEmpty();
	}

	@Test
	@DisplayName("T19: 남의 설치로는 연결을 저장하지 않는다")
	void linkRequiresOwnInstallation() {
		TestUser owner = rows.newUser("store-owner", clock.instant());
		long installationId = rows.insertLinkedInstallation(owner, clock.instant());
		TestUser other = rows.newUser("store-other", clock.instant());
		InstalledRepository repository = new InstalledRepository(GithubConnectionRows.newRepoId(), owner.login() + "/algo",
				"main", false);

		assertThatThrownBy(() -> store.link(other.id(), installationId, repository))
			.isInstanceOf(ConflictException.class);
		assertThat(rows.linksOf(other)).isEmpty();
	}

	private long suspendedInstallationOf(TestUser user) {
		long installationId = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(installationId, user.id(), user.githubId(), "User", user.githubId(), "SUSPENDED",
				clock.instant());
		return installationId;
	}

}
