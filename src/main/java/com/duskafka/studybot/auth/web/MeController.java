package com.duskafka.studybot.auth.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.auth.persistence.User;
import com.duskafka.studybot.auth.persistence.UserRepository;
import com.duskafka.studybot.common.error.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보 (api.md 4.4 {@code GET /api/me}, 권한: 로그인). 로그인하지 않았으면 보안 필터가 401로 답한다.
 * 연결된 레포(S07)와 알림 구독 상태(S13)는 그 단계에서 응답에 더한다.
 */
@RestController
@RequiredArgsConstructor
class MeController {

	private final UserRepository userRepository;

	@GetMapping("/api/me")
	MeResponse me(@AuthenticationPrincipal LoginUser loginUser) {
		User user = this.userRepository.findById(loginUser.userId())
			.orElseThrow(() -> new UnauthorizedException("로그인이 필요해요."));
		return new MeResponse(user.getId(), user.getGithubLogin(), user.getAvatarUrl());
	}

}
