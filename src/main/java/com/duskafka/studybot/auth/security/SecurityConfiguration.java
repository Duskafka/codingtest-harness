package com.duskafka.studybot.auth.security;

import com.duskafka.studybot.auth.login.GithubOAuth2UserService;
import com.duskafka.studybot.auth.login.NoStoreAuthorizedClientRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * 경로별 보안 (S04 만들 것 2·4·6·7, api.md 4.5, security.md 9.7).
 *
 * <ul>
 * <li>{@code /webhooks/**}: CSRF와 세션에서 뺀다. 인증은 서명으로만 한다 (서명 검증은 S06).</li>
 * <li>{@code /api/**}: 로그인이 필요하다. 로그인하지 않으면 401, CSRF 토큰이 없거나 틀리면 403
 * ({@code application/problem+json}). 공개 API(초대 미리보기 등)는 그 단계에서 여기에 하나씩 연다.</li>
 * <li>그 밖의 경로는 연다. Nginx가 Spring으로 넘기는 경로는 {@code /api/*}, {@code /oauth2/*}, {@code /login/*},
 * {@code /webhooks/*}뿐이고(architecture.md 4.1), {@code /oauth2/*}·{@code /login/*}은 아래 로그인 필터가 처리한다.
 * 외부에 노출하는 Actuator는 health뿐이다. 로그인이 필요한 화면 경로(S07의 {@code /github/setup})는 그 단계에서 규칙을 더한다.</li>
 * </ul>
 *
 * GitHub 로그인은 GitHub App의 사용자 인증이다 (결정 제안서 005). 인가 요청에는 PKCE가 붙는다 (Spring Security 7의
 * 기본값 {@code requireProofKey=true}). CSRF는 SPA용 공식 설정({@code csrf.spa()})이다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	/** 로그인에 성공하면 보내는 곳. 프론트의 홈이다 (api.md 4.5 그림 "302 홈"). */
	static final String LOGIN_SUCCESS_URL = "/";

	/** 로그인에 실패하면 보내는 곳. 프론트가 이 쿼리를 보고 실패를 알린다. */
	static final String LOGIN_FAILURE_URL = "/?login_error";

	@Bean
	@Order(1)
	SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) {
		http.securityMatcher("/webhooks/**")
			.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.requestCache(AbstractHttpConfigurer::disable);
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain appSecurityFilterChain(HttpSecurity http, GithubOAuth2UserService githubOAuth2UserService,
			NoStoreAuthorizedClientRepository authorizedClientRepository,
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
			ProblemDetailAccessDeniedHandler accessDeniedHandler) {
		http.authorizeHttpRequests(authorize -> authorize.requestMatchers("/api/**")
			.authenticated()
			.requestMatchers("/actuator/health")
			.permitAll()
			.anyRequest()
			.permitAll())
			.oauth2Login(login -> login
				// 로그인 화면은 프론트의 홈이다. 이 값을 정하면 Spring이 만드는 기본 로그인 페이지(/login)가 꺼진다.
				.loginPage(LOGIN_SUCCESS_URL)
				.authorizedClientRepository(authorizedClientRepository)
				.userInfoEndpoint(userInfo -> userInfo.userService(githubOAuth2UserService))
				.defaultSuccessUrl(LOGIN_SUCCESS_URL, true)
				.failureUrl(LOGIN_FAILURE_URL))
			.csrf(csrf -> csrf.spa())
			.logout(logout -> logout.logoutUrl("/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			// SPA라 로그인 전 요청을 저장했다가 되돌려 보내지 않는다. 로그인하면 늘 홈으로 간다.
			.requestCache(AbstractHttpConfigurer::disable);
		return http.build();
	}

}
