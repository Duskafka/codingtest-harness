package com.duskafka.studybot.auth.login;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * GitHub App의 사용자 토큰으로 {@code GET /user}를 불러 신원을 확인한다 (api.md 4.5, 결정 제안서 005).
 *
 * <p>응답에서 {@code id}, {@code login}, {@code avatar_url}만 꺼내 {@code users}에 반영하고, GitHub 프로필 대신
 * 최소한의 {@link com.duskafka.studybot.auth.LoginUser}를 인증 주체로 돌려준다. 토큰은 이 요청 안에서만 쓰고 버린다.
 * 응답 내용은 로그에 남기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class GithubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private final OAuth2UserService<OAuth2UserRequest, OAuth2User> githubUserInfo = new DefaultOAuth2UserService();

	private final LoginUserRegistrar registrar;

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) {
		OAuth2User github = this.githubUserInfo.loadUser(userRequest);
		Object id = github.getAttribute("id");
		Object login = github.getAttribute("login");
		Object avatarUrl = github.getAttribute("avatar_url");
		if (!(id instanceof Number githubId) || !(login instanceof String githubLogin)
				|| !StringUtils.hasText(githubLogin)) {
			throw new OAuth2AuthenticationException(
					new OAuth2Error("invalid_user_info", "GitHub 사용자 정보에 id나 login이 없다", null));
		}
		return this.registrar.register(githubId.longValue(), githubLogin,
				avatarUrl instanceof String url ? url : null);
	}

}
