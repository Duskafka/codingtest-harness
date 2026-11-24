package com.duskafka.studybot.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.github.GithubApiStub.Repo;
import com.duskafka.studybot.github.GithubConnectionRows.LinkRow;
import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/**
 * S07 수용 기준: 설치된 레포 목록과 풀이 레포 선택 (5.2 처리 순서 3, S07 만들 것 5·6, T19).
 *
 * <ul>
 * <li>{@code GET /api/me/github/repositories}: 내 설치의 레포를 GitHub API(설치 토큰)로 본다. 레포가 하나뿐이고 연결이 없으면 그 레포를
 * 연결한다 (사람이 정함, 2026-11-23 S07 세션 "레포 목록 조회 때").</li>
 * <li>{@code PUT /api/me/linked-repository}: 내 설치에 있는 레포만 고를 수 있다. 기본 브랜치를 조회해 저장하고 {@code repo_id}로 추적한다.
 * 사용자당 1개다.</li>
 * </ul>
 */
@ExtendWith(OutputCaptureExtension.class)
class GithubRepositoryLinkIntegrationTest extends GithubConnectionTestSupport {

	@Test
	@DisplayName("S07: 설치가 없으면 설치·연결 없이 빈 목록이고 GitHub를 부르지 않는다")
	void noInstallation() {
		TestUser user = newUser("no-install");

		MvcTestResult result = repositories(user);

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = json(result);
		assertThat(body.get("installation").isNull()).isTrue();
		assertThat(body.get("linkedRepository").isNull()).isTrue();
		assertThat(body.get("repositories").get("content")).isEmpty();
		assertThat(body.get("notice").asString()).contains("조직");
		assertThat(GithubApiStub.repositoryRequests()).isEmpty();
	}

	@Test
	@DisplayName("S07: 레포가 여러 개면 목록만 주고 자동으로 연결하지 않는다")
	void listsRepositoriesWithoutAutoLink() {
		TestUser user = newUser("many");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long first = GithubConnectionRows.newRepoId();
		long second = GithubConnectionRows.newRepoId();
		stubInstallation(installationId, Repo.of(first, user.login() + "/algo"),
				new Repo(second, user.login() + "/private-algo", "develop", true));

		JsonNode body = json(repositories(user));

		assertThat(body.get("installation").get("status").asString()).isEqualTo("ACTIVE");
		assertThat(body.get("installation").get("accountLogin").asString()).isEqualTo("account-" + user.githubId());
		assertThat(body.get("linkedRepository").isNull()).isTrue();
		JsonNode content = body.get("repositories").get("content");
		assertThat(content).hasSize(2);
		assertThat(content.get(1).get("repoId").asLong()).isEqualTo(second);
		assertThat(content.get(1).get("fullName").asString()).isEqualTo(user.login() + "/private-algo");
		assertThat(content.get(1).get("defaultBranch").asString()).isEqualTo("develop");
		assertThat(content.get(1).get("private").asBoolean()).isTrue();
		assertThat(content.get(1).get("linked").asBoolean()).isFalse();
		assertThat(body.get("repositories").get("page").get("totalElements").asLong()).isEqualTo(2);
		assertThat(rows.linksOf(user)).isEmpty();
	}

	@Test
	@DisplayName("S07: 설치한 레포가 하나면 목록을 볼 때 자동으로 연결한다")
	void singleRepositoryIsLinkedAutomatically() {
		TestUser user = newUser("single");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		stubInstallation(installationId, new Repo(repoId, user.login() + "/algo", "trunk", false));

		JsonNode body = json(repositories(user));

		assertThat(body.get("linkedRepository").get("repoId").asLong()).isEqualTo(repoId);
		assertThat(body.get("linkedRepository").get("status").asString()).isEqualTo("ACTIVE");
		assertThat(body.get("repositories").get("content").get(0).get("linked").asBoolean()).isTrue();
		assertThat(rows.linksOf(user)).singleElement().satisfies(link -> {
			assertThat(link.repoId()).isEqualTo(repoId);
			assertThat(link.installationId()).isEqualTo(installationId);
			assertThat(link.defaultBranch()).isEqualTo("trunk");
			assertThat(link.status()).isEqualTo("ACTIVE");
		});

		repositories(user);
		assertThat(rows.linksOf(user)).as("다시 봐도 연결은 하나").hasSize(1);
	}

	@Test
	@DisplayName("S07: 정지된 설치는 GitHub를 부르지 않고 정지 상태와 연결만 보여 준다")
	void suspendedInstallationDoesNotCallGithub() {
		TestUser user = newUser("suspended");
		long installationId = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(installationId, user.id(), user.githubId(), "User", user.githubId(), "SUSPENDED",
				clock.instant());
		rows.insertLink(user, installationId, GithubConnectionRows.newRepoId(), user.login() + "/algo", "SUSPENDED",
				clock.instant());

		JsonNode body = json(repositories(user));

		assertThat(body.get("installation").get("status").asString()).isEqualTo("SUSPENDED");
		assertThat(body.get("linkedRepository").get("status").asString()).isEqualTo("SUSPENDED");
		assertThat(body.get("repositories").get("content")).isEmpty();
		assertThat(GithubApiStub.tokenRequests(installationId)).isEmpty();
	}

	@Test
	@DisplayName("S07: 가입 때 연결하지 못한 내 설치(이벤트 유실 등)는 목록을 볼 때 다시 연결한다")
	void unclaimedInstallationIsClaimedOnView() {
		TestUser user = newUser("reclaim");
		long installationId = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(installationId, null, user.githubId(), "User", user.githubId(), "ACTIVE", clock.instant());
		stubInstallation(installationId, Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/a"),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/b"));

		JsonNode body = json(repositories(user));

		assertThat(rows.existingInstallation(installationId).userId()).isEqualTo(user.id());
		assertThat(body.get("repositories").get("content")).hasSize(2);
	}

	@Test
	@DisplayName("S07: 풀이 레포를 고르면 기본 브랜치를 조회해 저장한다")
	void linkRepository() {
		TestUser user = newUser("chooser");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long other = GithubConnectionRows.newRepoId();
		long chosen = GithubConnectionRows.newRepoId();
		stubInstallation(installationId, Repo.of(other, user.login() + "/other"),
				new Repo(chosen, user.login() + "/algo", "develop", true));

		MvcTestResult result = linkRepository(user, chosen);

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = json(result);
		assertThat(body.get("repoId").asLong()).isEqualTo(chosen);
		assertThat(body.get("fullName").asString()).isEqualTo(user.login() + "/algo");
		assertThat(body.get("defaultBranch").asString()).isEqualTo("develop");
		assertThat(body.get("status").asString()).isEqualTo("ACTIVE");
		assertThat(body.get("guide").asString()).contains("테스트 커밋");
		assertThat(rows.linksOf(user)).singleElement().satisfies(link -> {
			assertThat(link.repoId()).isEqualTo(chosen);
			assertThat(link.fullName()).isEqualTo(user.login() + "/algo");
			assertThat(link.defaultBranch()).isEqualTo("develop");
			assertThat(link.status()).isEqualTo("ACTIVE");
		});
	}

	@Test
	@DisplayName("S07: 다른 레포를 고르면 이전 연결은 비활성이 되고 사용자당 하나만 활성이다")
	void relinkReplacesPreviousLink() {
		TestUser user = newUser("switcher");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long first = GithubConnectionRows.newRepoId();
		long second = GithubConnectionRows.newRepoId();
		stubInstallation(installationId, Repo.of(first, user.login() + "/one"), Repo.of(second, user.login() + "/two"));

		assertThat(linkRepository(user, first)).hasStatus(HttpStatus.OK);
		assertThat(linkRepository(user, second)).hasStatus(HttpStatus.OK);

		List<LinkRow> links = rows.linksOf(user);
		assertThat(links).extracting(LinkRow::repoId).containsExactly(first, second);
		assertThat(links).extracting(LinkRow::status).containsExactly("INACTIVE", "ACTIVE");
	}

	@Test
	@DisplayName("S07: 같은 레포를 다시 고르면 연결은 그대로 하나다")
	void relinkSameRepositoryIsIdempotent() {
		TestUser user = newUser("same");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		stubInstallation(installationId, Repo.of(repoId, user.login() + "/algo"),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/other"));

		linkRepository(user, repoId);
		assertThat(linkRepository(user, repoId)).hasStatus(HttpStatus.OK);

		assertThat(rows.linksOf(user)).singleElement().extracting(LinkRow::status).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("S07: 레포 이름이 바뀌어도 repo_id로 같은 레포로 인식하고 저장한 이름을 고친다")
	void renamedRepositoryIsRecognizedByRepoId() {
		TestUser user = newUser("rename");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		long link = rows.insertLink(user, installationId, repoId, user.login() + "/old-name", "ACTIVE", clock.instant());
		stubInstallation(installationId, new Repo(repoId, user.login() + "/new-name", "main", false),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/other"));

		JsonNode body = json(repositories(user));

		assertThat(body.get("linkedRepository").get("repoId").asLong()).isEqualTo(repoId);
		assertThat(body.get("linkedRepository").get("fullName").asString()).isEqualTo(user.login() + "/new-name");
		assertThat(body.get("repositories").get("content").get(0).get("linked").asBoolean()).isTrue();
		assertThat(rows.link(link).fullName()).isEqualTo(user.login() + "/new-name");

		assertThat(linkRepository(user, repoId)).hasStatus(HttpStatus.OK);
		assertThat(rows.linksOf(user)).singleElement().extracting(LinkRow::id).isEqualTo(link);
	}

	@Test
	@DisplayName("S07: 고른 레포가 뒤 페이지에 있어도 찾는다")
	void linkFindsRepositoryOnLaterPage() {
		TestUser user = newUser("paged");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		GithubApiStub.stubInstallationToken(installationId, clock.instant().plusSeconds(3600));
		String token = GithubApiStub.tokenFor(installationId, 1);
		long first = GithubConnectionRows.newRepoId();
		long last = GithubConnectionRows.newRepoId();
		GithubApiStub.stubRepositoriesPage(token, 1, 2, Repo.of(first, user.login() + "/first"));
		GithubApiStub.stubRepositoriesPage(token, 2, 2, Repo.of(last, user.login() + "/last"));

		assertThat(linkRepository(user, last)).hasStatus(HttpStatus.OK);

		assertThat(rows.linksOf(user)).singleElement().extracting(LinkRow::repoId).isEqualTo(last);
	}

	@Test
	@DisplayName("S07: 목록의 page·size를 GitHub의 page(1부터)·per_page로 넘긴다")
	void paginationIsPassedThrough() {
		TestUser user = newUser("page");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		stubInstallation(installationId, Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/x"),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/y"));

		assertThat(get(user, "/api/me/github/repositories?page=1&size=1")).hasStatus(HttpStatus.OK);

		assertThat(GithubApiStub.repositoryRequests()).singleElement().satisfies(request -> {
			assertThat(request.queryParameter("page").firstValue()).isEqualTo("2");
			assertThat(request.queryParameter("per_page").firstValue()).isEqualTo("1");
		});
	}

	@Test
	@DisplayName("S07: 한 페이지는 100개까지다 (GitHub per_page 최대값)")
	void pageSizeIsLimited() {
		TestUser user = newUser("big-page");

		assertThat(get(user, "/api/me/github/repositories?size=101")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("S07: 설치하지 않았거나 설치가 정지됐으면 레포를 고를 수 없다 (409)")
	void linkRequiresActiveInstallation() {
		TestUser none = newUser("none");
		TestUser suspended = newUser("paused");
		long installationId = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(installationId, suspended.id(), suspended.githubId(), "User", suspended.githubId(),
				"SUSPENDED", clock.instant());

		assertThat(linkRepository(none, GithubConnectionRows.newRepoId())).hasStatus(HttpStatus.CONFLICT)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(linkRepository(suspended, GithubConnectionRows.newRepoId())).hasStatus(HttpStatus.CONFLICT);
		assertThat(rows.linksOf(none)).isEmpty();
		assertThat(rows.linksOf(suspended)).isEmpty();
	}

	@Test
	@DisplayName("T19: 남의 레포 목록은 보이지 않고, 남의 repo_id로는 연결할 수 없다 (404)")
	void t19CannotSeeOrLinkOthersRepositories() {
		TestUser owner = newUser("t19-owner");
		long ownerInstallation = rows.insertLinkedInstallation(owner, clock.instant());
		long ownerRepo = GithubConnectionRows.newRepoId();
		stubInstallation(ownerInstallation, Repo.of(ownerRepo, owner.login() + "/algo"),
				Repo.of(GithubConnectionRows.newRepoId(), owner.login() + "/other"));
		TestUser other = newUser("t19-other");
		long otherInstallation = rows.insertLinkedInstallation(other, clock.instant());
		long otherRepo = GithubConnectionRows.newRepoId();
		stubInstallation(otherInstallation, Repo.of(otherRepo, other.login() + "/mine"),
				Repo.of(GithubConnectionRows.newRepoId(), other.login() + "/mine2"));

		JsonNode otherView = json(repositories(other));
		MvcTestResult steal = linkRepository(other, ownerRepo);

		assertThat(otherView.get("repositories").get("content")).extracting(node -> node.get("repoId").asLong())
			.doesNotContain(ownerRepo)
			.contains(otherRepo);
		assertThat(steal).hasStatus(HttpStatus.NOT_FOUND).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(rows.linksOf(other)).isEmpty();
		assertThat(GithubApiStub.tokenRequests(ownerInstallation)).isEmpty();
	}

	@Test
	@DisplayName("S07: GitHub API가 실패하면 503 problem+json이고 토큰이 응답에 없다")
	void githubFailureIsServiceUnavailable() {
		TestUser user = newUser("outage");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		GithubApiStub.stubInstallationTokenFailure(installationId, 500);

		MvcTestResult list = repositories(user);
		MvcTestResult link = linkRepository(user, GithubConnectionRows.newRepoId());

		assertThat(list).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(link).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(body(list)).doesNotContain("ghs_");
	}

	@Test
	@DisplayName("S07: 레포 목록 조회가 실패해도 503이고, 받은 설치 토큰은 응답과 로그에 남지 않는다")
	void repositoryListFailureIsServiceUnavailable(CapturedOutput output) {
		TestUser user = newUser("list-outage");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		GithubApiStub.stubInstallationToken(installationId, clock.instant().plusSeconds(3600));
		GithubApiStub.stubRepositoriesFailure(GithubApiStub.tokenFor(installationId, 1), 502);

		MvcTestResult list = repositories(user);

		assertThat(list).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(body(list)).doesNotContain(GithubApiStub.tokenFor(installationId, 1));
		assertThat(output.getAll()).doesNotContain(GithubApiStub.tokenFor(installationId, 1))
			.contains("installation=" + installationId);
	}

	@Test
	@DisplayName("S07: 목록은 앞의 1,000개까지만 본다. 선택에서 찾을 수 있는 범위와 같다 (CodeRabbit 리뷰)")
	void listRangeMatchesSelectableRange() {
		TestUser user = newUser("range");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		stubInstallation(installationId, Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/x"),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/y"));

		assertThat(get(user, "/api/me/github/repositories?page=10&size=100")).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(get(user, "/api/me/github/repositories?page=9&size=100")).hasStatus(HttpStatus.OK);
	}

	@Test
	@DisplayName("S07: 설치의 레포 목록 전체가 한 페이지에 보이는데 연결 레포가 없으면 그 연결을 끊는다 (사람이 정함)")
	void linkMissingFromCompleteListIsDeactivated() {
		TestUser user = newUser("narrowed");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long gone = GithubConnectionRows.newRepoId();
		long link = rows.insertLink(user, installationId, gone, user.login() + "/gone", "ACTIVE", clock.instant());
		stubInstallation(installationId, Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/a"),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/b"));

		JsonNode body = json(repositories(user));

		assertThat(body.get("linkedRepository").isNull()).isTrue();
		assertThat(body.get("repositories").get("content")).extracting(node -> node.get("linked").asBoolean())
			.containsOnly(false);
		assertThat(rows.link(link).status()).isEqualTo("INACTIVE");
	}

	@Test
	@DisplayName("S07: 레포 목록이 여러 페이지면 한 페이지만 보고 연결을 끊지 않는다")
	void linkIsKeptWhenListIsPartial() {
		TestUser user = newUser("partial");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long elsewhere = GithubConnectionRows.newRepoId();
		long link = rows.insertLink(user, installationId, elsewhere, user.login() + "/later", "ACTIVE", clock.instant());
		GithubApiStub.stubInstallationToken(installationId, clock.instant().plusSeconds(3600));
		GithubApiStub.stubRepositoriesPage(GithubApiStub.tokenFor(installationId, 1), 1, 150,
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/first"));

		JsonNode body = json(repositories(user));

		assertThat(body.get("linkedRepository").get("repoId").asLong()).isEqualTo(elsewhere);
		assertThat(rows.link(link).status()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("S07: GitHub를 기다리는 사이 설치에서 레포가 빠지면(리비전이 오름) 고른 레포를 연결하지 않는다 (409, 요청 경로)")
	void linkIsRefusedWhenRepositoriesChangeDuringGithubCall() {
		TestUser user = newUser("removed-meanwhile");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long chosen = GithubConnectionRows.newRepoId();
		stubInstallation(installationId, Repo.of(chosen, user.login() + "/algo"),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/other"));
		GithubApiStub.beforeNextRepositoriesResponse(() -> changeInstallation(installationId,
				"repositories_revision = repositories_revision + 1"));

		MvcTestResult result = linkRepository(user, chosen);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(rows.linksOf(user)).isEmpty();
	}

	@Test
	@DisplayName("S07: GitHub를 기다리는 사이 설치가 정지되면 고른 레포를 연결하지 않는다 (409, 요청 경로)")
	void linkIsRefusedWhenInstallationIsSuspendedDuringGithubCall() {
		TestUser user = newUser("suspended-meanwhile");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long chosen = GithubConnectionRows.newRepoId();
		stubInstallation(installationId, Repo.of(chosen, user.login() + "/algo"),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/other"));
		GithubApiStub.beforeNextRepositoriesResponse(() -> changeInstallation(installationId, "status = 'SUSPENDED'"));

		MvcTestResult result = linkRepository(user, chosen);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(rows.linksOf(user)).isEmpty();
	}

	@Test
	@DisplayName("S07: GitHub를 기다리는 사이 레포가 빠지면 레포가 하나여도 자동으로 연결하지 않는다 (요청 경로)")
	void autoLinkIsSkippedWhenRepositoriesChangeDuringGithubCall() {
		TestUser user = newUser("auto-meanwhile");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		stubInstallation(installationId, Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/only"));
		GithubApiStub.beforeNextRepositoriesResponse(() -> changeInstallation(installationId,
				"repositories_revision = repositories_revision + 1"));

		JsonNode body = json(repositories(user));

		assertThat(body.get("linkedRepository").isNull()).isTrue();
		assertThat(rows.linksOf(user)).isEmpty();
	}

	@Test
	@DisplayName("S07: GitHub를 기다리는 사이 연결이 새 행으로 바뀌면, 목록에 그 레포가 없어도 새 연결은 끊지 않는다 (CodeRabbit 2차 리뷰)")
	void linkReplacedDuringListIsNotDeactivated() {
		TestUser user = newUser("replaced-meanwhile");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		long observed = rows.insertLink(user, installationId, repoId, user.login() + "/algo", "ACTIVE", clock.instant());
		stubInstallation(installationId, Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/a"),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/b"));
		AtomicLong replacement = new AtomicLong();
		GithubApiStub.beforeNextRepositoriesResponse(() -> {
			changeLinkStatus(observed, "INACTIVE");
			replacement.set(rows.insertLink(user, installationId, repoId, user.login() + "/algo", "ACTIVE",
					clock.instant()));
		});

		JsonNode body = json(repositories(user));

		assertThat(rows.link(replacement.get()).status()).as("목록을 읽기 전에 본 연결 행만 끊는다").isEqualTo("ACTIVE");
		assertThat(body.get("linkedRepository").get("repoId").asLong()).isEqualTo(repoId);
	}

	@Test
	@DisplayName("S07: 지금 설치의 목록으로는 다른 설치의 연결을 끊지 않는다 (CodeRabbit 2차 리뷰)")
	void linkOfAnotherInstallationIsNotJudgedByThisList() {
		TestUser user = newUser("other-installation");
		long older = GithubConnectionRows.newInstallationId();
		rows.insertInstallation(older, user.id(), user.githubId(), "User", user.githubId(), "ACTIVE",
				clock.instant().minusSeconds(3600));
		long current = rows.insertLinkedInstallation(user, clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		long link = rows.insertLink(user, older, repoId, user.login() + "/algo", "ACTIVE", clock.instant());
		stubInstallation(current, Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/a"),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/b"));

		JsonNode body = json(repositories(user));

		assertThat(rows.link(link).status()).isEqualTo("ACTIVE");
		assertThat(body.get("linkedRepository").get("repoId").asLong()).isEqualTo(repoId);
	}

	@Test
	@DisplayName("S07: GitHub를 기다리는 사이 연결이 다른 레포로 바뀌면 옛 연결이 아니라 지금 연결을 보여 준다 (CodeRabbit 리뷰)")
	void linkChangedDuringListIsReadAgain() {
		TestUser user = newUser("changed-meanwhile");
		long installationId = rows.insertLinkedInstallation(user, clock.instant());
		long before = GithubConnectionRows.newRepoId();
		long after = GithubConnectionRows.newRepoId();
		long observed = rows.insertLink(user, installationId, before, user.login() + "/before", "ACTIVE",
				clock.instant());
		stubInstallation(installationId, Repo.of(before, user.login() + "/before"),
				Repo.of(GithubConnectionRows.newRepoId(), user.login() + "/other"));
		GithubApiStub.beforeNextRepositoriesResponse(() -> {
			changeLinkStatus(observed, "INACTIVE");
			rows.insertLink(user, installationId, after, user.login() + "/after", "ACTIVE", clock.instant());
		});

		JsonNode body = json(repositories(user));

		assertThat(body.get("linkedRepository").get("repoId").asLong()).isEqualTo(after);
		assertThat(body.get("repositories").get("content")).extracting(node -> node.get("linked").asBoolean())
			.containsOnly(false);
	}

	/** 다른 요청이 연결을 바꾼 것처럼 다른 DB 연결에서 바꾸고 커밋한다. */
	private void changeLinkStatus(long linkId, String status) {
		jdbc.sql("update studybot.linked_repositories set status = :status where id = :id")
			.param("status", status)
			.param("id", linkId)
			.update();
	}

	/** 웹훅이 설치를 바꾼 것처럼 다른 연결에서 바꾸고 커밋한다. */
	private void changeInstallation(long installationId, String assignment) {
		jdbc.sql("update studybot.github_installations set " + assignment + " where installation_id = :id")
			.param("id", installationId)
			.update();
	}

}
