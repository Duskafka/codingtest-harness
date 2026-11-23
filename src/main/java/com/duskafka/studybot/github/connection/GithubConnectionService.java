package com.duskafka.studybot.github.connection;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.github.api.GithubApiClient;
import com.duskafka.studybot.github.api.GithubApiUnavailableException;
import com.duskafka.studybot.github.api.GithubAppProperties;
import com.duskafka.studybot.github.api.InstallationTokenCache;
import com.duskafka.studybot.github.api.InstalledRepository;
import com.duskafka.studybot.github.api.RepositoryPage;
import com.duskafka.studybot.github.connection.ConnectionViews.InstallationView;
import com.duskafka.studybot.github.connection.ConnectionViews.LinkView;
import com.duskafka.studybot.github.connection.ConnectionViews.RepositoriesView;
import com.duskafka.studybot.github.connection.ConnectionViews.RepositoryChoice;
import com.duskafka.studybot.github.installation.GithubInstallationLinker;
import com.duskafka.studybot.github.persistence.InstallationStatus;
import com.duskafka.studybot.github.persistence.LinkedRepositoryStatus;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * GitHub 연결 화면 (5.2, S07 만들 것 5·6). 사용자는 자기 설치와 그 설치의 레포만 다룬다 (T19).
 *
 * <ul>
 * <li>내 설치는 DB의 {@code user_id}로만 찾는다. 그 값은 서명된 웹훅의 설치자 정보로만 채워진다. 요청이나 Setup URL의
 * {@code installation_id}는 쓰지 않는다 [S37].</li>
 * <li>레포 목록은 내 설치의 설치 토큰으로 GitHub에 묻는다. 남의 레포는 내 설치 토큰으로 보이지 않는다.</li>
 * <li>화면을 볼 때마다 가입 전 설치 연결({@link GithubInstallationLinker#claim})을 다시 한다. 로그인 뒤 이벤트가 사라졌거나
 * 웹훅과 가입이 겹쳤어도 여기서 맞춰진다.</li>
 * </ul>
 */
@Slf4j
@Service
public class GithubConnectionService {

	/**
	 * 목록으로 볼 수 있고 고를 수 있는 레포의 수 (앞에서부터). 목록과 선택의 범위를 같게 둔다: 목록에 보인 레포는 선택에서도 찾는다
	 * (PR #14 리뷰 반영). 개인 풀이 레포를 고르는 데 충분하다.
	 */
	public static final int MAX_VISIBLE_REPOSITORIES = 1000;

	/** 레포를 고를 때 찾아볼 최대 페이지 수 (100개씩, {@link #MAX_VISIBLE_REPOSITORIES}개까지). */
	static final int MAX_LOOKUP_PAGES = MAX_VISIBLE_REPOSITORIES / GithubApiClient.MAX_PER_PAGE;

	private final GithubInstallationLinker linker;

	private final GithubConnectionStore store;

	private final InstallStates states;

	private final InstallationTokenCache tokens;

	private final GithubApiClient github;

	private final GithubAppProperties properties;

	GithubConnectionService(GithubInstallationLinker linker, GithubConnectionStore store, InstallStates states,
			InstallationTokenCache tokens, GithubApiClient github, GithubAppProperties properties) {
		this.linker = linker;
		this.store = store;
		this.states = states;
		this.tokens = tokens;
		this.github = github;
		this.properties = properties;
	}

	/** App 설치 주소. 서명된 1회용 state를 붙인다. */
	public URI installUrl(long userId, HttpSession session) {
		return UriComponentsBuilder.fromUri(this.properties.webBaseUrl())
			.path("/apps/{slug}/installations/new")
			.queryParam("state", this.states.issue(userId, session))
			.buildAndExpand(this.properties.slug())
			.encode()
			.toUri();
	}

	/**
	 * Setup URL로 돌아왔다. state가 이 사용자가 이 세션에서 받은 유효한 것이면 소모하고 {@code true}. 연결은 하지 않는다
	 * (연결은 웹훅이 한다).
	 */
	public boolean completeSetup(long userId, @Nullable String state, HttpSession session) {
		return this.states.consume(userId, state, session);
	}

	/**
	 * 내 설치와 그 레포 목록 한 페이지. 레포가 하나뿐이고 연결이 없으면 그 레포를 연결한다 (S07 만들 것 6, 사람이 정함 2026-11-23).
	 * 연결한 레포가 목록에 이름·기본 브랜치가 바뀌어 나오면 저장한 값을 고친다 ({@code repo_id}로 같은 레포).
	 *
	 * @param page 0부터
	 * @param size 1~{@value GithubApiClient#MAX_PER_PAGE}
	 */
	public RepositoriesView repositories(LoginUser user, int page, int size) {
		if ((long) (page + 1) * size > MAX_VISIBLE_REPOSITORIES) {
			throw new BadRequestException("레포 목록은 앞의 %,d개까지 볼 수 있어요.".formatted(MAX_VISIBLE_REPOSITORIES));
		}
		this.linker.claim(user.userId(), user.githubId());
		Optional<InstallationView> installation = this.store.currentInstallation(user.userId());
		LinkView link = this.store.currentLink(user.userId()).orElse(null);
		PageRequest pageable = PageRequest.of(page, size);
		if (installation.isEmpty() || installation.get().status() != InstallationStatus.ACTIVE) {
			return new RepositoriesView(installation.orElse(null), link, Page.empty(pageable));
		}
		long installationId = installation.get().installationId();
		RepositoryPage found = listRepositories(installationId, page + 1, size);
		if (link != null) {
			link = followLinkedRepository(user.userId(), link, page, found);
		}
		if (link == null && found.totalCount() == 1 && found.repositories().size() == 1) {
			link = autoLink(user.userId(), installationId, installation.get().repositoriesRevision(),
					found.repositories().getFirst());
		}
		long linkedRepoId = link != null ? link.repoId() : -1L;
		List<RepositoryChoice> choices = found.repositories()
			.stream()
			.map(repository -> new RepositoryChoice(repository.id(), repository.fullName(), repository.defaultBranch(),
					repository.isPrivate(), repository.id() == linkedRepoId))
			.toList();
		return new RepositoriesView(installation.get(), link, new PageImpl<>(choices, pageable, found.totalCount()));
	}

	/**
	 * GitHub에서 읽은 목록에 비춰 연결을 맞추고 지금 연결을 돌려준다.
	 * <ul>
	 * <li>연결한 레포가 목록에 이름·기본 브랜치가 바뀌어 나오면 저장한 값을 고친다 ({@code repo_id}로 같은 레포).</li>
	 * <li>목록 전체가 이 페이지에 다 보이는데(첫 페이지, 개수 = 전체 수) 연결한 레포가 없으면 연결을 끊는다. '모든 레포'→'고른 레포'로
	 * 바꾸면 GitHub가 빠진 레포를 알려 주지 않기 때문이다 (사람이 정함, 2026-11-23 "목록이 한 페이지로 다 보일 때만 끊음").
	 * 목록이 여러 페이지면 판단하지 않는다.</li>
	 * <li>GitHub를 기다리는 사이 연결이 바뀌었으면 옛 연결을 쓰지 않고 지금 연결을 다시 읽는다 (PR #14 리뷰 반영).</li>
	 * </ul>
	 */
	private @Nullable LinkView followLinkedRepository(long userId, LinkView link, int page, RepositoryPage found) {
		for (InstalledRepository repository : found.repositories()) {
			if (repository.id() == link.repoId()) {
				return this.store.refresh(userId, repository).or(() -> this.store.currentLink(userId)).orElse(null);
			}
		}
		boolean complete = page == 0 && found.repositories().size() == found.totalCount();
		if (complete && link.status() == LinkedRepositoryStatus.ACTIVE) {
			return this.store.deactivateMissing(userId, link.repoId()).orElse(null);
		}
		return link;
	}

	/**
	 * 풀이 레포를 고른다. 내 설치(ACTIVE)의 레포 목록에 있는 레포만 고를 수 있다. 기본 브랜치는 GitHub에서 조회한 값을 저장한다.
	 *
	 * @throws ConflictException 설치가 없거나 정지됨, 또는 그 레포가 이미 다른 사용자에게 연결됨
	 * @throws NotFoundException 내 설치에 그 레포가 없음 (남의 레포인지 드러내지 않는다)
	 */
	public LinkView link(LoginUser user, long repoId) {
		this.linker.claim(user.userId(), user.githubId());
		InstallationView installation = this.store.currentInstallation(user.userId())
			.orElseThrow(() -> new ConflictException("GitHub App을 먼저 설치해 주세요."));
		if (installation.status() != InstallationStatus.ACTIVE) {
			throw new ConflictException("GitHub App 설치가 정지돼 있어요. GitHub 설정에서 정지를 풀어 주세요.");
		}
		InstalledRepository repository = findRepository(installation.installationId(), repoId)
			.orElseThrow(() -> new NotFoundException("설치한 레포에서 찾지 못했어요. GitHub App 설정에서 이 레포를 더해 주세요."));
		try {
			return this.store.link(user.userId(), installation.installationId(), installation.repositoriesRevision(),
					repository);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("이 레포는 이미 다른 연결이 쓰고 있어요. 잠시 뒤 다시 시도해 주세요.");
		}
	}

	private Optional<InstalledRepository> findRepository(long installationId, long repoId) {
		int seen = 0;
		for (int page = 1; page <= MAX_LOOKUP_PAGES; page++) {
			RepositoryPage found = listRepositories(installationId, page, GithubApiClient.MAX_PER_PAGE);
			for (InstalledRepository repository : found.repositories()) {
				if (repository.id() == repoId) {
					return Optional.of(repository);
				}
			}
			seen += found.repositories().size();
			if (found.repositories().isEmpty() || seen >= found.totalCount()) {
				break;
			}
		}
		return Optional.empty();
	}

	private RepositoryPage listRepositories(long installationId, int githubPage, int perPage) {
		String token = this.tokens.tokenFor(installationId);
		try {
			return this.github.listRepositories(installationId, token, githubPage, perPage);
		}
		catch (GithubApiUnavailableException ex) {
			// 토큰이 취소됐을 수도 있으니 버리고, 다음 요청에서 새로 받는다.
			this.tokens.evict(installationId);
			throw ex;
		}
	}

	private @Nullable LinkView autoLink(long userId, long installationId, long expectedRevision,
			InstalledRepository repository) {
		try {
			return this.store.linkIfNone(userId, installationId, expectedRevision, repository).orElse(null);
		}
		catch (DataIntegrityViolationException ex) {
			// 같은 사용자의 다른 요청이 먼저 연결했다. 그 연결을 쓴다.
			log.info("자동 연결이 다른 요청과 겹쳤어요: user={}", userId);
			return this.store.currentLink(userId).orElse(null);
		}
	}

}
