package com.duskafka.studybot.github.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.github.api.GithubApiClient;
import com.duskafka.studybot.github.connection.GithubConnectionService;
import com.duskafka.studybot.github.web.GithubConnectionResponses.InstallUrlResponse;
import com.duskafka.studybot.github.web.GithubConnectionResponses.LinkRepositoryRequest;
import com.duskafka.studybot.github.web.GithubConnectionResponses.LinkResponse;
import com.duskafka.studybot.github.web.GithubConnectionResponses.RepositoriesResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GitHub 연결 API (api.md 4.4, S07 만들 것 5·6). 권한은 모두 '로그인'이고, 사용자는 자기 설치·레포만 다룬다 (T19).
 * 설치를 사용자에게 잇는 일은 여기서 하지 않는다. 서명된 웹훅의 설치자 정보로만 한다 (5.2, T21).
 */
@RestController
@RequiredArgsConstructor
class GithubConnectionController {

	static final int DEFAULT_PAGE_SIZE = 30;

	private final GithubConnectionService service;

	/** App 설치 주소 (서명된 1회용 state 포함). */
	@GetMapping("/api/github/install-url")
	InstallUrlResponse installUrl(@AuthenticationPrincipal LoginUser loginUser, HttpSession session) {
		return new InstallUrlResponse(this.service.installUrl(loginUser.userId(), session).toString(),
				GithubConnectionResponses.ORGANIZATION_NOTICE);
	}

	/** 내 설치와 설치된 레포 목록. 레포가 하나뿐이면 자동으로 연결한다. GitHub 페이지네이션을 그대로 넘긴다 (page는 0부터). */
	@GetMapping("/api/me/github/repositories")
	RepositoriesResponse repositories(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
			@RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(GithubApiClient.MAX_PER_PAGE) int size) {
		return RepositoriesResponse.from(this.service.repositories(loginUser, page, size));
	}

	/** 풀이 레포 선택. 사용자당 1개. */
	@PutMapping("/api/me/linked-repository")
	LinkResponse link(@AuthenticationPrincipal LoginUser loginUser, @Valid @RequestBody LinkRepositoryRequest request) {
		return LinkResponse.from(this.service.link(loginUser, request.repoId()));
	}

}
