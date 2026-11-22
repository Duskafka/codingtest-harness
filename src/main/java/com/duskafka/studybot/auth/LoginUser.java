package com.duskafka.studybot.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 로그인한 사용자. 세션(DB)에 저장되는 인증 주체다 (D22).
 *
 * <p>GitHub {@code /user} 응답 전체가 아니라 우리 사용자 ID와 GitHub ID만 담는다. 이메일 같은 프로필 정보가
 * 세션 테이블에 남지 않게 하기 위해서다 (security.md 9.8 '최소 수집'). GitHub access token도 담지 않는다 (api.md 4.5).
 * 주체 이름({@link #getName()})은 우리 사용자 ID라서, 사용자별 세션 조회(Spring Session의 PRINCIPAL_NAME)에 쓸 수 있다.
 *
 * <p>컨트롤러에서는 {@code @AuthenticationPrincipal LoginUser}로 받는다.
 *
 * @param userId {@code users.id}
 * @param githubId GitHub 사용자 ID ({@code users.github_id})
 */
public record LoginUser(long userId, long githubId) implements OAuth2User, Serializable {

	private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

	@Override
	public String getName() {
		return Long.toString(this.userId);
	}

	/** GitHub 프로필 속성은 보관하지 않는다. */
	@Override
	public Map<String, Object> getAttributes() {
		return Map.of();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return AUTHORITIES;
	}

}
