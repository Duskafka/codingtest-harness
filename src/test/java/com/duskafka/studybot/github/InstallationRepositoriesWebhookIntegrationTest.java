package com.duskafka.studybot.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import com.duskafka.studybot.github.InstallationPayloads.Account;
import com.duskafka.studybot.github.InstallationPayloads.Repo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S07 수용 기준: {@code installation_repositories} 웹훅 처리 (5.2 처리 순서 4, S07 만들 것 3).
 * 설치에서 레포가 빠지면 그 레포의 연결을 비활성으로 바꾼다. 레포는 이름이 아니라 {@code repo_id}로 찾는다.
 */
class InstallationRepositoriesWebhookIntegrationTest extends WebhookTestSupport {

	private GithubConnectionRows rows;

	@BeforeEach
	void setUpRows() {
		rows = new GithubConnectionRows(jdbc);
	}

	@Test
	@DisplayName("S07: installation_repositories removed가 오면 그 레포의 연결이 비활성이 된다")
	void removedDeactivatesLink() {
		TestUser user = rows.newUser("remover", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		long link = rows.insertLink(user, installationId, repoId, user.login() + "/algo", "ACTIVE", clock.instant());
		Account account = Account.user(user.githubId());
		String delivery = newDeliveryId();

		sendSigned("installation_repositories", delivery, InstallationPayloads.installationRepositories("removed",
				installationId, account, account, List.of(), List.of(new Repo(repoId, user.login() + "/algo"))));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		assertThat(rows.link(link).status()).isEqualTo("INACTIVE");
	}

	@Test
	@DisplayName("S07: 레포 이름이 바뀌어도 repo_id로 같은 레포로 보고 연결을 끊는다")
	void removedMatchesByRepoIdNotName() {
		TestUser user = rows.newUser("renamer", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		long link = rows.insertLink(user, installationId, repoId, user.login() + "/old-name", "ACTIVE", clock.instant());
		Account account = Account.user(user.githubId());

		sendSigned("installation_repositories", newDeliveryId(), InstallationPayloads.installationRepositories("removed",
				installationId, account, account, List.of(), List.of(new Repo(repoId, user.login() + "/new-name"))));

		assertThat(rows.link(link).status()).isEqualTo("INACTIVE");
	}

	// 이 PR에서 처음 쓴 테스트를 리뷰 반영으로 고쳤다(CodeRabbit: 제거와 새 연결 저장의 경합). removed는 연결 유무와 관계없이
	// 설치의 레포 목록 리비전을 올리므로 PROCESSED다 (이전 기대값 IGNORED).
	@Test
	@DisplayName("S07: 연결하지 않은 다른 레포가 빠지면 연결은 그대로이고, 설치의 레포 목록 리비전만 오른다")
	void removedOtherRepoKeepsLink() {
		TestUser user = rows.newUser("keeper", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long link = rows.insertLink(user, installationId, GithubConnectionRows.newRepoId(), user.login() + "/algo",
				"ACTIVE", clock.instant());
		Account account = Account.user(user.githubId());
		String delivery = newDeliveryId();

		sendSigned("installation_repositories", delivery,
				InstallationPayloads.installationRepositories("removed", installationId, account, account, List.of(),
						List.of(new Repo(GithubConnectionRows.newRepoId(), user.login() + "/other"))));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		assertThat(rows.link(link).status()).isEqualTo("ACTIVE");
		assertThat(rows.existingInstallation(installationId).repositoriesRevision()).isEqualTo(1);
	}

	@Test
	@DisplayName("S07: '모든 레포'→'고른 레포'로 바꿔 removed가 빈 목록으로 와도 리비전이 오른다")
	void removedWithEmptyListBumpsRevision() {
		TestUser user = rows.newUser("selection", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		Account account = Account.user(user.githubId());
		String delivery = newDeliveryId();

		sendSigned("installation_repositories", delivery, InstallationPayloads.installationRepositories("removed",
				installationId, account, account, List.of(), List.of()));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		assertThat(rows.existingInstallation(installationId).repositoriesRevision()).isEqualTo(1);
	}

	@Test
	@DisplayName("S07: 모르는 설치의 removed는 IGNORED")
	void removedForUnknownInstallationIsIgnored() {
		long unknown = GithubConnectionRows.newInstallationId();
		Account account = Account.user(GithubConnectionRows.newGithubId());
		String delivery = newDeliveryId();

		sendSigned("installation_repositories", delivery, InstallationPayloads.installationRepositories("removed",
				unknown, account, account, List.of(), List.of(new Repo(GithubConnectionRows.newRepoId(), "x/y"))));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("IGNORED");
		assertThat(rows.installation(unknown)).isEmpty();
	}

	@Test
	@DisplayName("S07: 같은 레포 ID라도 다른 설치의 removed로는 연결이 끊기지 않는다")
	void removedFromAnotherInstallationKeepsLink() {
		TestUser user = rows.newUser("mine", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		long link = rows.insertLink(user, installationId, repoId, user.login() + "/algo", "ACTIVE", clock.instant());
		TestUser other = rows.newUser("theirs", clock.instant());
		long otherInstallation = rows.insertLinkedInstallation(other, clock.instant());
		Account otherAccount = Account.user(other.githubId());

		sendSigned("installation_repositories", newDeliveryId(),
				InstallationPayloads.installationRepositories("removed", otherInstallation, otherAccount, otherAccount,
						List.of(), List.of(new Repo(repoId, user.login() + "/algo"))));

		assertThat(rows.link(link).status()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("S07: installation_repositories added는 바꿀 것이 없어 IGNORED (레포 목록은 GitHub API로 본다)")
	void addedIsIgnored() {
		TestUser user = rows.newUser("adder", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long link = rows.insertLink(user, installationId, GithubConnectionRows.newRepoId(), user.login() + "/algo",
				"ACTIVE", clock.instant());
		Account account = Account.user(user.githubId());
		String delivery = newDeliveryId();

		sendSigned("installation_repositories", delivery,
				InstallationPayloads.installationRepositories("added", installationId, account, account,
						List.of(new Repo(GithubConnectionRows.newRepoId(), user.login() + "/more")), List.of()));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("IGNORED");
		assertThat(rows.linksOf(user)).singleElement().satisfies(row -> {
			assertThat(row.id()).isEqualTo(link);
			assertThat(row.status()).isEqualTo("ACTIVE");
		});
	}

}
