package com.duskafka.studybot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.github.GithubConnectionRows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S07 수용 기준: 설치를 먼저 하고 나중에 가입하면 가입할 때 연결된다 (5.2 처리 순서 2, S07 만들 것 2).
 *
 * <p>가입은 실제 로그인 흐름(GitHub 콜백)으로 한다. 설치는 웹훅이 남겼을 모양 그대로 DB에 넣는다 (웹훅 처리 자체는
 * {@code github.InstallationWebhookIntegrationTest}가 본다). 가입할 때도 웹훅과 같은 조건(개인 계정, 계정 = 설치자)을 다시 본다.
 */
class SignupLinksGithubInstallationIntegrationTest extends LoginTestSupport {

	private GithubConnectionRows rows;

	@BeforeEach
	void setUpRows() {
		rows = new GithubConnectionRows(jdbc);
	}

	@Test
	@DisplayName("S07: 설치를 먼저 하고 나중에 가입하면 가입할 때 그 설치가 연결된다")
	void signupLinksEarlierInstallation() {
		long githubId = GithubConnectionRows.newGithubId();
		long installationId = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(installationId, null, githubId, "User", githubId, "ACTIVE", clock.instant());

		login(githubId, "s07-late-signup", "https://avatars.example.com/u/" + githubId);

		assertThat(rows.existingInstallation(installationId).userId()).isEqualTo(userIdOf(githubId));
	}

	@Test
	@DisplayName("S07: 가입해도 조직 계정 설치나 남이 설치한 것(account.id ≠ sender.id)은 연결되지 않는다")
	void signupDoesNotLinkIneligibleInstallations() {
		long githubId = GithubConnectionRows.newGithubId();
		long organization = GithubConnectionRows.newInstallationId();
		long installedBySomeoneElse = GithubConnectionRows.newInstallationId();
		long deleted = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(organization, null, githubId, "Organization", githubId, "ACTIVE", clock.instant());
		rows.insertInstallation(installedBySomeoneElse, null, githubId, "User", GithubConnectionRows.newGithubId(),
				"ACTIVE", clock.instant());
		rows.insertInstallation(deleted, null, githubId, "User", githubId, "DELETED", clock.instant());

		login(githubId, "s07-ineligible", "https://avatars.example.com/u/" + githubId);

		assertThat(rows.existingInstallation(organization).userId()).isNull();
		assertThat(rows.existingInstallation(installedBySomeoneElse).userId()).isNull();
		assertThat(rows.existingInstallation(deleted).userId()).isNull();
	}

	@Test
	@DisplayName("S07: 이미 다른 사용자에게 연결된 설치는 로그인으로 옮겨 가지 않는다")
	void loginDoesNotStealLinkedInstallation() {
		long githubId = GithubConnectionRows.newGithubId();
		var owner = rows.newUser("owner", clock.instant());
		long installationId = GithubConnectionRows.newInstallationId();
		// 실제로는 생길 수 없는 모양(계정 ID가 로그인하는 사람과 같은데 다른 사용자에게 연결됨)으로, 이미 정해진 user_id를 덮어쓰지 않는지 본다.
		rows.insertInstallation(installationId, owner.id(), githubId, "User", githubId, "ACTIVE", clock.instant());

		login(githubId, "s07-no-steal", "https://avatars.example.com/u/" + githubId);

		assertThat(rows.existingInstallation(installationId).userId()).isEqualTo(owner.id());
	}

}
