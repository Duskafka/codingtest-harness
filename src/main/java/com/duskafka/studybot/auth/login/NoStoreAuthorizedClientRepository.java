package com.duskafka.studybot.auth.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

/**
 * 인가된 클라이언트(GitHub access token·refresh token)를 아무 데도 저장하지 않는 저장소.
 *
 * <p>GitHub 토큰은 로그인할 때 신원 확인에만 쓰고 저장하지 않는다 (api.md 4.5, 결정 제안서 005 '양쪽 공통').
 * Spring Boot가 기본으로 만드는 저장소는 로그인 뒤 토큰을 메모리({@code InMemoryOAuth2AuthorizedClientService})에
 * 계속 들고 있어서, 이 빈으로 대신한다. 이 빈이 있으면 Boot의 기본 저장소는 만들어지지 않는다.
 * 서비스가 사용자 대신 GitHub API를 부를 일이 생기면(설치 토큰과 별개로) 이 결정을 다시 연다.
 */
@Component
public class NoStoreAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

	@Override
	public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(String clientRegistrationId,
			Authentication principal, HttpServletRequest request) {
		return null;
	}

	@Override
	public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
			HttpServletRequest request, HttpServletResponse response) {
		// 저장하지 않는다.
	}

	@Override
	public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
			HttpServletRequest request, HttpServletResponse response) {
		// 저장한 것이 없다.
	}

}
