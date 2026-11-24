package com.duskafka.studybot.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.github.GithubConnectionRows.InstallationRow;
import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import com.duskafka.studybot.github.InstallationPayloads.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S07 수용 기준: {@code installation} 웹훅 처리 (5.2 처리 순서 1·2·4, S07 만들 것 1·2).
 * 연결은 서명된 웹훅의 설치자 정보로만 한다. 계정이 개인(User)이고 {@code account.id == sender.id}일 때만 그 GitHub ID의 사용자에게 잇는다.
 */
class InstallationWebhookIntegrationTest extends WebhookTestSupport {

	private GithubConnectionRows rows;

	@BeforeEach
	void setUpRows() {
		rows = new GithubConnectionRows(jdbc);
	}

	@Test
	@DisplayName("S07: 개인 계정 설치(account.id == sender.id)는 그 GitHub ID의 사용자에게 연결된다")
	void createdByOwnerLinksToUser() {
		TestUser user = rows.newUser("installer", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();
		String delivery = newDeliveryId();

		assertThat(sendSigned("installation", delivery, InstallationPayloads.installation("created", installationId,
				Account.user(user.githubId()), Account.user(user.githubId()))))
			.hasStatus(202);

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		InstallationRow installation = rows.existingInstallation(installationId);
		assertThat(installation.userId()).isEqualTo(user.id());
		assertThat(installation.accountId()).isEqualTo(user.githubId());
		assertThat(installation.accountType()).isEqualTo("User");
		assertThat(installation.status()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("S07: account.id와 sender.id가 다르면 설치만 남기고 사용자에게 연결하지 않는다")
	void createdBySomeoneElseIsNotLinked() {
		TestUser owner = rows.newUser("owner", clock.instant());
		TestUser other = rows.newUser("other", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();

		sendSigned("installation", newDeliveryId(), InstallationPayloads.installation("created", installationId,
				Account.user(owner.githubId()), Account.user(other.githubId())));

		InstallationRow installation = rows.existingInstallation(installationId);
		assertThat(installation.userId()).isNull();
		assertThat(installation.installedByOwner()).isFalse();
	}

	@Test
	@DisplayName("S07: 조직 계정 설치는 계정 ID가 사용자와 같아도 사용자에게 연결하지 않는다")
	void organizationInstallationIsNotLinked() {
		TestUser user = rows.newUser("org-admin", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();

		// 계정 종류만 다르게 해서, 종류 검사가 빠지면 연결되는 경우를 만든다.
		sendSigned("installation", newDeliveryId(), InstallationPayloads.installation("created", installationId,
				Account.organization(user.githubId()), Account.user(user.githubId())));

		InstallationRow installation = rows.existingInstallation(installationId);
		assertThat(installation.userId()).isNull();
		assertThat(installation.accountType()).isEqualTo("Organization");
	}

	@Test
	@DisplayName("S07: 가입하지 않은 사람의 설치는 저장만 하고 사용자는 비워 둔다")
	void installationBeforeSignupIsStoredUnlinked() {
		long githubId = GithubConnectionRows.newGithubId();
		long installationId = GithubConnectionRows.newInstallationId();
		String delivery = newDeliveryId();

		sendSigned("installation", delivery, InstallationPayloads.installation("created", installationId,
				Account.user(githubId), Account.user(githubId)));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		InstallationRow installation = rows.existingInstallation(installationId);
		assertThat(installation.userId()).isNull();
		assertThat(installation.accountId()).isEqualTo(githubId);
		assertThat(installation.installedByOwner()).isTrue();
		assertThat(installation.status()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("S07: 같은 설치 이벤트가 다른 전달로 두 번 와도 설치는 하나이고 결과가 같다")
	void createdTwiceIsIdempotent() {
		TestUser user = rows.newUser("twice", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();
		byte[] body = InstallationPayloads.installation("created", installationId, Account.user(user.githubId()),
				Account.user(user.githubId()));
		String first = newDeliveryId();
		String second = newDeliveryId();

		sendSigned("installation", first, body);
		sendSigned("installation", second, body);

		assertThat(onlyInboxRow(first).status()).isEqualTo("PROCESSED");
		assertThat(onlyInboxRow(second).status()).isEqualTo("PROCESSED");
		assertThat(jdbc.sql("select count(*) from studybot.github_installations where installation_id = :id")
			.param("id", installationId)
			.query(Long.class)
			.single()).isEqualTo(1L);
		assertThat(rows.existingInstallation(installationId).userId()).isEqualTo(user.id());
	}

	@Test
	@DisplayName("S07: installation deleted → 설치는 DELETED, 그 설치의 레포 연결은 비활성")
	void deletedDeactivatesLinks() {
		TestUser user = rows.newUser("deleter", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long link = rows.insertLink(user, installationId, GithubConnectionRows.newRepoId(), user.login() + "/algo",
				"ACTIVE", clock.instant());
		String delivery = newDeliveryId();

		sendSigned("installation", delivery, InstallationPayloads.installation("deleted", installationId,
				Account.user(user.githubId()), Account.user(user.githubId())));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		assertThat(rows.existingInstallation(installationId).status()).isEqualTo("DELETED");
		assertThat(rows.existingInstallation(installationId).deletedAt()).as("9.8 30일 정리의 기준 시각")
			.isEqualTo(clock.instant());
		assertThat(rows.link(link).status()).isEqualTo("INACTIVE");
	}

	@Test
	@DisplayName("S07: installation suspend → 연결 비활성(SUSPENDED), unsuspend → 다시 활성")
	void suspendAndUnsuspend() {
		TestUser user = rows.newUser("suspender", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long link = rows.insertLink(user, installationId, GithubConnectionRows.newRepoId(), user.login() + "/algo",
				"ACTIVE", clock.instant());
		Account account = Account.user(user.githubId());

		String suspend = newDeliveryId();
		sendSigned("installation", suspend, InstallationPayloads.installation("suspend", installationId, account, account));

		assertThat(onlyInboxRow(suspend).status()).isEqualTo("PROCESSED");
		assertThat(rows.existingInstallation(installationId).status()).isEqualTo("SUSPENDED");
		assertThat(rows.existingInstallation(installationId).deletedAt()).isNull();
		assertThat(rows.link(link).status()).isEqualTo("SUSPENDED");

		String unsuspend = newDeliveryId();
		sendSigned("installation", unsuspend,
				InstallationPayloads.installation("unsuspend", installationId, account, account));

		assertThat(onlyInboxRow(unsuspend).status()).isEqualTo("PROCESSED");
		assertThat(rows.existingInstallation(installationId).status()).isEqualTo("ACTIVE");
		assertThat(rows.link(link).status()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("S07: 정지 전에 이미 꺼진 연결(INACTIVE)은 unsuspend로 되살아나지 않는다")
	void unsuspendDoesNotReviveInactiveLinks() {
		TestUser user = rows.newUser("revive", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long removed = rows.insertLink(user, installationId, GithubConnectionRows.newRepoId(), user.login() + "/old",
				"INACTIVE", clock.instant());
		long current = rows.insertLink(user, installationId, GithubConnectionRows.newRepoId(), user.login() + "/new",
				"ACTIVE", clock.instant());
		Account account = Account.user(user.githubId());

		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("suspend", installationId, account, account));
		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("unsuspend", installationId, account, account));

		assertThat(rows.link(removed).status()).isEqualTo("INACTIVE");
		assertThat(rows.link(current).status()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("S07: 정지된 동안 같은 레포가 다른 연결로 활성이 됐으면 unsuspend로 되살리지 않고 끊는다 (레포당 활성 하나)")
	void unsuspendDoesNotCreateSecondActiveLinkForSameRepo() {
		TestUser previous = rows.newUser("prev-owner", clock.instant());
		long previousInstallation = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(previousInstallation, previous.id(), previous.githubId(), "User", previous.githubId(),
				"SUSPENDED", clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		long suspended = rows.insertLink(previous, previousInstallation, repoId, "someone/algo", "SUSPENDED",
				clock.instant());
		// 레포 이전처럼 정상 흐름에서는 드문 모양: 그동안 같은 레포가 다른 사용자에게 활성으로 연결됐다.
		TestUser current = rows.newUser("new-owner", clock.instant());
		long currentInstallation = rows.insertLinkedInstallation(current, clock.instant());
		long active = rows.insertLink(current, currentInstallation, repoId, "someone/algo", "ACTIVE", clock.instant());
		Account account = Account.user(previous.githubId());
		String delivery = newDeliveryId();

		sendSigned("installation", delivery,
				InstallationPayloads.installation("unsuspend", previousInstallation, account, account));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		assertThat(rows.existingInstallation(previousInstallation).status()).isEqualTo("ACTIVE");
		assertThat(rows.link(suspended).status()).isEqualTo("INACTIVE");
		assertThat(rows.link(active).status()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("S07: 삭제된 설치는 suspend·unsuspend가 늦게 와도 되살아나지 않는다")
	void deletedInstallationStaysDeleted() {
		TestUser user = rows.newUser("late", clock.instant());
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		Account account = Account.user(user.githubId());
		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("deleted", installationId, account, account));

		String unsuspend = newDeliveryId();
		sendSigned("installation", unsuspend,
				InstallationPayloads.installation("unsuspend", installationId, account, account));

		assertThat(onlyInboxRow(unsuspend).status()).isEqualTo("IGNORED");
		assertThat(rows.existingInstallation(installationId).status()).isEqualTo("DELETED");
	}

	// 이 PR에서 처음 쓴 테스트를 리뷰 반영으로 나눴다(CodeRabbit: 순서가 바뀐 웹훅). 모르는 설치의 deleted·suspend는 이제
	// 행을 남기고 PROCESSED다. 다루지 않는 action은 그대로 IGNORED다.
	@Test
	@DisplayName("S07: 다루지 않는 action(new_permissions_accepted)은 IGNORED이고 설치는 그대로")
	void unhandledActionIsIgnored() {
		TestUser user = rows.newUser("perm", clock.instant());
		long known = rows.insertLinkedInstallation(user, clock.instant());
		String permissions = newDeliveryId();

		sendSigned("installation", permissions, InstallationPayloads.installation("new_permissions_accepted", known,
				Account.user(user.githubId()), Account.user(user.githubId())));

		assertThat(onlyInboxRow(permissions).status()).isEqualTo("IGNORED");
		assertThat(rows.existingInstallation(known).status()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("S07: deleted가 created보다 먼저 와도(전달 순서는 보장되지 않음) 늦은 created가 설치를 되살리지 않는다")
	void deletedBeforeCreatedStaysDeleted() {
		TestUser user = rows.newUser("out-of-order", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();
		Account account = Account.user(user.githubId());
		String deleted = newDeliveryId();
		String created = newDeliveryId();

		sendSigned("installation", deleted, InstallationPayloads.installation("deleted", installationId, account, account));
		sendSigned("installation", created, InstallationPayloads.installation("created", installationId, account, account));

		assertThat(onlyInboxRow(deleted).status()).isEqualTo("PROCESSED");
		assertThat(onlyInboxRow(created).status()).isEqualTo("PROCESSED");
		assertThat(rows.existingInstallation(installationId).status()).isEqualTo("DELETED");
		assertThat(rows.existingInstallation(installationId).userId()).as("삭제된 설치는 잇지 않는다").isNull();
	}

	@Test
	@DisplayName("S07: suspend가 created보다 먼저 와도 정지 상태가 남고, unsuspend가 오면 활성이 된다")
	void suspendBeforeCreatedStaysSuspended() {
		TestUser user = rows.newUser("early-suspend", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();
		Account account = Account.user(user.githubId());

		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("suspend", installationId, account, account));
		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("created", installationId, account, account));

		assertThat(rows.existingInstallation(installationId).status()).isEqualTo("SUSPENDED");
		assertThat(rows.existingInstallation(installationId).userId()).isEqualTo(user.id());

		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("unsuspend", installationId, account, account));

		assertThat(rows.existingInstallation(installationId).status()).isEqualTo("ACTIVE");
	}

	// 이 PR에서 처음 쓴 테스트를 2차 리뷰 반영으로 고쳤다(CodeRabbit: 정지·삭제 이벤트의 sender는 설치자가 아님).
	// 설치자는 created에서만 확인한다(5.2 처리 순서 1). 처음 보는 설치의 unsuspend는 상태만 남기고 잇지 않는다.
	@Test
	@DisplayName("S07: 처음 보는 설치의 unsuspend는 활성 설치로 남고, 사용자에게는 created가 온 뒤에 잇는다")
	void unknownUnsuspendIsRecordedActiveAndLinkedAfterCreated() {
		TestUser user = rows.newUser("early-unsuspend", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();
		Account account = Account.user(user.githubId());
		String delivery = newDeliveryId();

		sendSigned("installation", delivery,
				InstallationPayloads.installation("unsuspend", installationId, account, account));

		assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED");
		assertThat(rows.existingInstallation(installationId).status()).isEqualTo("ACTIVE");
		assertThat(rows.existingInstallation(installationId).userId()).as("unsuspend의 sender는 설치자 증거가 아니다").isNull();

		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("created", installationId, account, account));

		assertThat(rows.existingInstallation(installationId).status()).isEqualTo("ACTIVE");
		assertThat(rows.existingInstallation(installationId).userId()).isEqualTo(user.id());
	}

	// 이 PR에서 처음 쓴 테스트를 2차 리뷰 반영으로 고쳤다. 처음 보는 설치의 deleted는 설치 ID와 상태만 남긴다.
	@Test
	@DisplayName("S07: 처음 보는 설치의 deleted는 삭제 이력으로 남고, 계정·설치자는 created가 올 때까지 비워 둔다")
	void unknownDeletedIsRecordedWithoutInstaller() {
		long unknown = GithubConnectionRows.newInstallationId();
		long owner = GithubConnectionRows.newGithubId();
		String deleted = newDeliveryId();

		sendSigned("installation", deleted, InstallationPayloads.installation("deleted", unknown,
				Account.organization(owner), Account.user(GithubConnectionRows.newGithubId())));

		assertThat(onlyInboxRow(deleted).status()).isEqualTo("PROCESSED");
		InstallationRow installation = rows.existingInstallation(unknown);
		assertThat(installation.status()).isEqualTo("DELETED");
		assertThat(installation.deletedAt()).isEqualTo(clock.instant());
		assertThat(installation.accountId()).isNull();
		assertThat(installation.accountType()).isNull();
		assertThat(installation.installedByOwner()).isNull();
	}

	@Test
	@DisplayName("S07: 다른 사람(App 관리자)의 suspend가 created보다 먼저 와도, 주인이 설치했으면 created 뒤에 주인에게 잇는다")
	void suspendBySomeoneElseBeforeCreatedStillLinksOwner() {
		TestUser owner = rows.newUser("suspended-by-admin", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();
		Account account = Account.user(owner.githubId());
		Account admin = Account.user(GithubConnectionRows.newGithubId());

		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("suspend", installationId, account, admin));
		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("created", installationId, account, account));

		InstallationRow installation = rows.existingInstallation(installationId);
		assertThat(installation.status()).as("먼저 온 정지 상태는 그대로").isEqualTo("SUSPENDED");
		assertThat(installation.installedByOwner()).isTrue();
		assertThat(installation.userId()).isEqualTo(owner.id());
	}

	@Test
	@DisplayName("S07: 주인의 suspend가 created보다 먼저 와도, created의 설치자가 주인이 아니면 잇지 않는다")
	void suspendByOwnerBeforeCreatedBySomeoneElseIsNotLinked() {
		TestUser owner = rows.newUser("suspended-by-owner", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();
		Account account = Account.user(owner.githubId());
		Account someoneElse = Account.user(GithubConnectionRows.newGithubId());

		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("suspend", installationId, account, account));
		sendSigned("installation", newDeliveryId(),
				InstallationPayloads.installation("created", installationId, account, someoneElse));

		InstallationRow installation = rows.existingInstallation(installationId);
		assertThat(installation.status()).isEqualTo("SUSPENDED");
		assertThat(installation.installedByOwner()).isFalse();
		assertThat(installation.userId()).isNull();
	}

	@Test
	@DisplayName("S07: installation.account가 없는 deleted가 created보다 먼저 와도 삭제 이력이 남고, 늦은 created가 되살리지 않는다")
	void deletedWithoutAccountBeforeCreatedStaysDeleted() {
		TestUser user = rows.newUser("deleted-no-account", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();
		Account account = Account.user(user.githubId());
		String deleted = newDeliveryId();
		String created = newDeliveryId();

		sendSigned("installation", deleted,
				InstallationPayloads.installationWithoutAccount("deleted", installationId, account));
		sendSigned("installation", created, InstallationPayloads.installation("created", installationId, account, account));

		assertThat(onlyInboxRow(deleted).status()).isEqualTo("PROCESSED");
		assertThat(onlyInboxRow(created).status()).isEqualTo("PROCESSED");
		InstallationRow installation = rows.existingInstallation(installationId);
		assertThat(installation.status()).isEqualTo("DELETED");
		assertThat(installation.userId()).as("삭제된 설치는 잇지 않는다").isNull();
		assertThat(installation.accountId()).as("쓰지 않을 계정 값은 남기지 않는다 (9.8 최소 수집)").isNull();
		assertThat(installation.installedByOwner()).isNull();
	}

}
