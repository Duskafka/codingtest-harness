package com.duskafka.studybot.auth.security;

import com.duskafka.studybot.auth.login.GithubOAuth2UserService;
import com.duskafka.studybot.auth.login.NoStoreAuthorizedClientRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * 경로별 보안 (S04 만들 것 2·4·6·7, api.md 4.5, security.md 9.7).
 *
 * <ul>
 * <li>{@code /webhooks/**}: CSRF와 세션에서 뺀다. 인증은 서명으로만 한다 (서명 검증은 S06).</li>
 * <li>{@code /api/**}: 로그인이 필요하다. 로그인하지 않으면 401, CSRF 토큰이 없거나 틀리면 403
 * ({@code application/problem+json}). 공개 API는 그 단계에서 여기에 하나씩 연다: 초대 미리보기 {@code GET /api/invitations/{token}}
 * (S05).</li>
 * <li>{@code /github/setup}(S07): GitHub App 설치 뒤 브라우저가 돌아오는 화면 경로라 로그인이 필요하다. 메서드와 관계없이
 * 막는다(Spring MVC는 {@code @GetMapping}을 HEAD에도 연결한다). 로그인하지 않았으면 401 JSON 대신 로그인 화면(프론트 홈)으로
 * 보낸다.</li>
 * <li>그 밖의 경로는 연다. 그 경로들은 이렇다: {@code /oauth2/*}·{@code /login/*}은 아래 로그인 필터가 처리하고,
 * {@code POST /logout}은 로그아웃 필터가 처리하며(CSRF 토큰 필요), 외부에 노출하는 Actuator는 health뿐이다.</li>
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

	/** 로그인 없이 볼 수 있는 API: 초대 미리보기 {@code GET /api/invitations/{token}} (S05). */
	static final String PUBLIC_INVITATION_PREVIEW = "/api/invitations/*";

	/** GitHub App Setup URL (S07). 로그인이 필요한 화면 경로다. */
	static final String GITHUB_SETUP = "/github/setup";

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
		http.authorizeHttpRequests(authorize -> authorize
			// 초대 미리보기는 공개다 (api.md 4.4, S05). 경로 한 칸만 열어서 수락(/accept, 로그인)은 아래 규칙을 따른다.
			.requestMatchers(HttpMethod.GET, PUBLIC_INVITATION_PREVIEW)
			.permitAll()
			.requestMatchers("/api/**")
			.authenticated()
			.requestMatchers(GITHUB_SETUP)
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
				// 기본값은 로그인 결과에 요청 정보(접속 IP, 로그인 전 세션 ID)를 붙여 세션과 함께 DB에 저장한다.
				// 세션은 30일 롤링이라 접속 기록이 끝없이 남을 수 있어서 붙이지 않는다 (security.md 9.8).
				.authenticationDetailsSource(request -> null)
				.defaultSuccessUrl(LOGIN_SUCCESS_URL, true)
				.failureUrl(LOGIN_FAILURE_URL))
			.csrf(csrf -> csrf.spa())
			.logout(logout -> logout.logoutUrl("/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint(authenticationEntryPoint))
				.accessDeniedHandler(accessDeniedHandler))
			// SPA라 로그인 전 요청을 저장했다가 되돌려 보내지 않는다. 로그인하면 늘 홈으로 간다.
			.requestCache(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/**
	 * 로그인하지 않은 요청에 답하는 방법. API는 401 problem+json이고, 브라우저가 화면으로 오는 {@code /github/setup}만
	 * 로그인 화면(프론트 홈)으로 보낸다(HEAD도 GET과 같게). 로그인하면 늘 홈으로 가므로 원래 주소를 기억하지 않는다.
	 */
	private static AuthenticationEntryPoint entryPoint(ProblemDetailAuthenticationEntryPoint problemEntryPoint) {
		AuthenticationEntryPoint toLoginPage = (request, response, ex) -> response.sendRedirect(LOGIN_SUCCESS_URL);
		return DelegatingAuthenticationEntryPoint.builder()
			.addEntryPointFor(toLoginPage, PathPatternRequestMatcher.pathPattern(GITHUB_SETUP))
			.defaultEntryPoint(problemEntryPoint)
			.build();
	}

}
