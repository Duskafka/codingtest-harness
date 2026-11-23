package com.duskafka.studybot.github.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.github.connection.GithubConnectionService;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * GitHub App Setup URL ({@code GET /github/setup}, api.md 4.4, S07 만들 것 5). 설치나 레포 변경 뒤 GitHub가 브라우저를 여기로 보낸다.
 *
 * <p>연결 상태 안내만 한다. 쿼리의 {@code installation_id}는 위조될 수 있어 읽지 않고, 넘기지도 않는다
 * [S37](<a href="https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/about-the-setup-url">GitHub 문서</a>).
 * state를 확인·소모하고 프론트 홈으로 보낸다: 확인되면 {@code /?github_setup=ok}, 아니면(state 없음·만료·재사용, GitHub 설정에서
 * 레포를 바꾸고 돌아온 경우 등) {@code /?github_setup=unverified}. 프론트는 레포 목록 API로 상태를 보여 준다
 * (사람이 정함, 2026-11-23 S07 세션 "프론트 홈으로 302").
 */
@Controller
@RequiredArgsConstructor
class GithubSetupController {

	static final URI VERIFIED = URI.create("/?github_setup=ok");

	static final URI UNVERIFIED = URI.create("/?github_setup=unverified");

	private final GithubConnectionService service;

	@GetMapping("/github/setup")
	ResponseEntity<Void> setup(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(name = "state", required = false) @Nullable String state, HttpSession session) {
		boolean verified = this.service.completeSetup(loginUser.userId(), state, session);
		return ResponseEntity.status(HttpStatus.FOUND).location(verified ? VERIFIED : UNVERIFIED).build();
	}

}
