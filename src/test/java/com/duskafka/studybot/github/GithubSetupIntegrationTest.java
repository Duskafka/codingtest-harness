package com.duskafka.studybot.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/**
 * S07 수용 기준: 설치 주소와 Setup URL (5.2, S07 만들 것 5, T21).
 *
 * <p>{@code GET /github/setup}은 연결 상태 안내만 한다. 쿼리의 {@code installation_id}는 위조될 수 있어 읽지 않는다 [S37].
 * 설치 주소에 붙인 서명된 1회용 state를 확인·소모하고 프론트 홈으로 보낸다 (사람이 정함, 2026-11-23 S07 세션 "프론트 홈으로 302").
 */
class GithubSetupIntegrationTest extends GithubConnectionTestSupport {

	private static final String INSTALL_URL_PREFIX = "https://github.com/apps/studybot-test-app/installations/new?state=";

	@Test
	@DisplayName("S07: 설치 주소는 App 설치 페이지에 서명된 state를 붙이고, 개인 레포를 쓰라는 안내를 담는다")
	void installUrlCarriesStateAndNotice() {
		TestUser user = newUser("install-url");

		MvcTestResult result = get(user, "/api/github/install-url");

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = json(result);
		assertThat(body.get("url").asString()).startsWith(INSTALL_URL_PREFIX);
		assertThat(new Browser(user).installState()).isNotBlank();
		assertThat(body.get("notice").asString()).contains("조직").contains("개인");
	}

	@Test
	@DisplayName("S07: 로그인하지 않으면 설치 주소를 받을 수 없다 (401)")
	void installUrlRequiresLogin() {
		assertThat(mvc.get().uri("/api/github/install-url").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("T21: Setup URL에 남의 installation_id를 넣어도 연결되지 않는다")
	void t21ForgedInstallationIdDoesNotLink() {
		TestUser victim = newUser("victim");
		long victimInstallation = rows.insertLinkedInstallation(victim, clock.instant());
		long unclaimedOwner = GithubConnectionRows.newGithubId();
		long unclaimedInstallation = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(unclaimedInstallation, null, unclaimedOwner, "User", unclaimedOwner, "ACTIVE",
				clock.instant());
		TestUser attacker = newUser("attacker");
		Browser browser = new Browser(attacker);

		MvcTestResult linked = browser.get("/github/setup?installation_id=" + victimInstallation
				+ "&setup_action=install&state=" + browser.installState());
		MvcTestResult unclaimed = browser
			.get("/github/setup?installation_id=" + unclaimedInstallation + "&setup_action=install");

		assertThat(linked).hasStatus(HttpStatus.FOUND);
		assertThat(linked.getResponse().getHeader("Location")).isEqualTo("/?github_setup=ok");
		assertThat(unclaimed.getResponse().getHeader("Location")).isEqualTo("/?github_setup=unverified");
		assertThat(rows.existingInstallation(victimInstallation).userId()).isEqualTo(victim.id());
		assertThat(rows.existingInstallation(unclaimedInstallation).userId()).isNull();
		JsonNode attackerView = json(repositories(attacker));
		assertThat(attackerView.get("installation").isNull()).isTrue();
		assertThat(attackerView.get("repositories").get("content")).isEmpty();
		assertThat(GithubApiStub.tokenRequests(victimInstallation)).isEmpty();
	}

	@Test
	@DisplayName("S07: state는 한 번만 쓸 수 있다. 두 번째는 unverified")
	void stateIsSingleUse() {
		TestUser user = newUser("once");
		Browser browser = new Browser(user);
		String state = browser.installState();

		MvcTestResult first = browser.get("/github/setup?setup_action=install&state=" + state);
		MvcTestResult second = browser.get("/github/setup?setup_action=install&state=" + state);

		assertThat(first.getResponse().getHeader("Location")).isEqualTo("/?github_setup=ok");
		assertThat(second.getResponse().getHeader("Location")).isEqualTo("/?github_setup=unverified");
	}

	@Test
	@DisplayName("T19: 남이 받은 state로는 확인되지 않는다")
	void t19OtherUsersStateIsNotAccepted() {
		TestUser owner = newUser("state-owner");
		TestUser other = newUser("state-thief");
		String ownersState = new Browser(owner).installState();
		Browser otherBrowser = new Browser(other);
		otherBrowser.installState();

		MvcTestResult result = otherBrowser.get("/github/setup?setup_action=install&state=" + ownersState);

		assertThat(result.getResponse().getHeader("Location")).isEqualTo("/?github_setup=unverified");
	}

	@Test
	@DisplayName("T19: 같은 브라우저 세션이라도 다른 사용자로 로그인했으면 앞 사용자의 state는 확인되지 않는다")
	void t19StateIsBoundToUserNotOnlySession() {
		TestUser first = newUser("first-login");
		TestUser second = newUser("second-login");
		Browser browser = new Browser(first);
		String firstState = browser.installState();

		MvcTestResult result = browser.loggedInAs(second).get("/github/setup?setup_action=install&state=" + firstState);

		assertThat(result.getResponse().getHeader("Location")).isEqualTo("/?github_setup=unverified");
	}

	@Test
	@DisplayName("S07: state가 없거나, 고쳤거나, 30분이 지났으면 unverified")
	void missingTamperedOrExpiredStateIsUnverified() {
		TestUser user = newUser("expired");
		Browser browser = new Browser(user);
		String state = browser.installState();
		// 끝 글자는 base64의 남는 비트만 바꿀 수 있어서, 서명한 내용의 첫 글자를 바꾼다.
		String tampered = (state.charAt(0) == 'A' ? "B" : "A") + state.substring(1);

		MvcTestResult missing = browser.get("/github/setup?setup_action=update");
		MvcTestResult changed = browser.get("/github/setup?setup_action=install&state=" + tampered);
		clock.advance(Duration.ofMinutes(31));
		MvcTestResult expired = browser.get("/github/setup?setup_action=install&state=" + state);

		assertThat(missing.getResponse().getHeader("Location")).isEqualTo("/?github_setup=unverified");
		assertThat(changed.getResponse().getHeader("Location")).isEqualTo("/?github_setup=unverified");
		assertThat(expired.getResponse().getHeader("Location")).isEqualTo("/?github_setup=unverified");
	}

	@Test
	@DisplayName("S07: 로그인하지 않고 Setup URL로 오면 로그인 화면(프론트 홈)으로 보낸다")
	void setupWithoutLoginRedirectsHome() {
		MvcTestResult result = mvc.get().uri("/github/setup?installation_id=1&setup_action=install").exchange();

		assertThat(result).hasStatus(HttpStatus.FOUND);
		assertThat(result.getResponse().getHeader("Location")).isEqualTo("/");
	}

	@Test
	@DisplayName("S07: HEAD도 GET과 같다. 로그인하지 않았으면 프론트 홈으로 보내고 핸들러에 닿지 않는다 (CodeRabbit 리뷰)")
	void headWithoutLoginRedirectsHome() {
		MvcTestResult result = mvc.method(HttpMethod.HEAD).uri("/github/setup?installation_id=1").exchange();

		assertThat(result).hasStatus(HttpStatus.FOUND);
		assertThat(result.getResponse().getHeader("Location")).isEqualTo("/");
	}

	@Test
	@DisplayName("S07: 로그인한 HEAD는 GET과 같은 안내 주소로 보낸다")
	void headWithLoginRedirectsLikeGet() {
		TestUser user = newUser("head");

		MvcTestResult result = mvc.method(HttpMethod.HEAD)
			.uri("/github/setup?setup_action=update")
			.with(as(user))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FOUND);
		assertThat(result.getResponse().getHeader("Location")).isEqualTo("/?github_setup=unverified");
	}

}
