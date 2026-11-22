package com.duskafka.studybot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 세션과 쿠키 (S04 만들 것 3, api.md 4.5, security.md 9.7, 결정 제안서 002).
 * 세션은 DB에 저장하고, 쿠키는 __Host-Http- 접두사·HttpOnly·Secure·SameSite=Lax, 만료는 30일 롤링이다.
 */
class SessionCookieIntegrationTest extends LoginTestSupport {

	@Test
	@DisplayName("S04: 세션 쿠키 이름이 __Host-Http-로 시작하고 HttpOnly·Secure·SameSite=Lax·Path=/가 붙으며 Domain이 없다")
	void sessionCookieHasHostHttpPrefixAndSecureAttributes() {
		Login login = login(5101L, "cookie-user", "https://avatars.example.com/u/5101");

		for (MvcTestResult result : List.of(login.authorization(), login.callback())) {
			String header = setCookieHeader(result, SESSION_COOKIE).orElseThrow();
			assertThat(header).startsWith("__Host-Http-");
			List<String> attributes = attributesOf(header);
			assertThat(attributes).contains("Path=/", "Secure", "HttpOnly", "SameSite=Lax");
			assertThat(attributes).noneMatch(attribute -> attribute.regionMatches(true, 0, "Domain=", 0, 7));
		}
	}

	@Test
	@DisplayName("S04: 세션 쿠키 만료는 30일이다 (Max-Age=2592000)")
	void sessionCookieLastsThirtyDays() {
		Login login = login(5201L, "cookie-age", "https://avatars.example.com/u/5201");

		String header = setCookieHeader(login.callback(), SESSION_COOKIE).orElseThrow();
		assertThat(attributesOf(header)).contains("Max-Age=" + THIRTY_DAYS_SECONDS);
	}

	@Test
	@DisplayName("S04: 세션이 있는 요청마다 쿠키를 다시 내려 줘서 브라우저 쪽 만료도 30일씩 굴러간다 (롤링)")
	void sessionCookieIsRefreshedOnEveryRequest() {
		Login login = login(5301L, "cookie-rolling", "https://avatars.example.com/u/5301");

		MvcTestResult next = withSession(mvc.get().uri("/api/me"), login.session()).exchange();

		assertThat(next).hasStatus(HttpStatus.OK);
		String header = setCookieHeader(next, SESSION_COOKIE).orElseThrow(
				() -> new AssertionError("세션이 있는 요청에 세션 쿠키를 다시 내려 주지 않았다"));
		assertThat(cookieValue(next, SESSION_COOKIE)).contains(login.session());
		assertThat(attributesOf(header)).contains("Max-Age=" + THIRTY_DAYS_SECONDS, "Secure", "HttpOnly",
				"SameSite=Lax", "Path=/");
	}

	@Test
	@DisplayName("S04: 세션은 DB(studybot.spring_session)에 저장되고 비활성 만료가 30일이다")
	void sessionIsStoredInDatabaseWithThirtyDayInactivity() {
		login(5401L, "cookie-db", "https://avatars.example.com/u/5401");
		String principalName = String.valueOf(userIdOf(5401L));

		List<Integer> maxInactive = jdbc
			.sql("select max_inactive_interval from studybot.spring_session where principal_name = :name")
			.param("name", principalName)
			.query(Integer.class)
			.list();

		assertThat(maxInactive).isNotEmpty().containsOnly((int) THIRTY_DAYS_SECONDS);
	}

	/*
	 * 만료 경계. Spring Session은 내부에서 시스템 시각(Instant.now)으로 만료를 판단하고 주입한 Clock을 받지 않는다.
	 * 그래서 MutableClock을 옮기는 대신, 세션 행의 마지막 접근 시각을 DB 시각 기준으로 과거로 돌려 '그만큼 요청이 없었던'
	 * 상황을 만든다 (S04 PR 판단 기록).
	 */

	@Test
	@DisplayName("S04: 요청이 오면 서버 쪽 세션 만료 시각도 그때부터 30일로 밀린다 (롤링)")
	void serverSideExpiryMovesForwardOnRequest() {
		Login login = login(5601L, "cookie-expiry-roll", "https://avatars.example.com/u/5601");
		String principalName = String.valueOf(userIdOf(5601L));
		long tenDays = 10L * 24 * 60 * 60 * 1000;
		pretendIdleFor(principalName, tenDays);
		long expiryBefore = expiryTimeOf(principalName);

		assertThat(withSession(mvc.get().uri("/api/me"), login.session())).hasStatus(HttpStatus.OK);

		long expiryAfter = expiryTimeOf(principalName);
		assertThat(expiryAfter - expiryBefore).as("만료 시각이 약 10일 밀린다").isGreaterThan(tenDays - 60_000);
	}

	@Test
	@DisplayName("S04: 30일이 되기 직전까지 요청이 없었어도 세션은 살아 있다")
	void sessionIsAliveJustBeforeThirtyDaysOfInactivity() {
		Login login = login(5701L, "cookie-expiry-alive", "https://avatars.example.com/u/5701");
		pretendIdleFor(String.valueOf(userIdOf(5701L)), THIRTY_DAYS_SECONDS * 1000 - 60 * 60 * 1000);

		assertThat(withSession(mvc.get().uri("/api/me"), login.session())).hasStatus(HttpStatus.OK);
	}

	@Test
	@DisplayName("S04: 30일 넘게 요청이 없었으면 세션이 만료되어 401이 된다")
	void sessionExpiresAfterThirtyDaysOfInactivity() {
		Login login = login(5801L, "cookie-expiry-dead", "https://avatars.example.com/u/5801");
		pretendIdleFor(String.valueOf(userIdOf(5801L)), THIRTY_DAYS_SECONDS * 1000 + 60 * 1000);

		assertThat(withSession(mvc.get().uri("/api/me"), login.session())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	/** 이 사용자의 세션이 마지막 요청 뒤 이만큼 지난 것처럼 마지막 접근 시각과 만료 시각을 DB 시각 기준으로 돌린다. */
	private void pretendIdleFor(String principalName, long idleMillis) {
		int updated = jdbc.sql("""
				update studybot.spring_session
				set last_access_time = cast(extract(epoch from now()) * 1000 as bigint) - :idle,
					expiry_time = cast(extract(epoch from now()) * 1000 as bigint) - :idle
						+ cast(max_inactive_interval as bigint) * 1000
				where principal_name = :name
				""").param("idle", idleMillis).param("name", principalName).update();
		assertThat(updated).isEqualTo(1);
	}

	private long expiryTimeOf(String principalName) {
		return jdbc.sql("select expiry_time from studybot.spring_session where principal_name = :name")
			.param("name", principalName)
			.query(Long.class)
			.single();
	}

	@Test
	@DisplayName("S04: 로그아웃(POST /logout)하면 204이고 세션이 지워져 같은 쿠키로는 401이 된다")
	void logoutInvalidatesSession() {
		Login login = login(5501L, "cookie-logout", "https://avatars.example.com/u/5501");
		String principalName = String.valueOf(userIdOf(5501L));
		String csrf = csrfTokenFor(login);

		MvcTestResult logout = withSession(mvc.post().uri("/logout"), login.session())
			.cookie(new Cookie(XSRF_COOKIE, csrf))
			.header(XSRF_HEADER, csrf)
			.exchange();

		assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(setCookieHeader(logout, SESSION_COOKIE)).hasValueSatisfying(
				header -> assertThat(attributesOf(header)).contains("Max-Age=0"));
		assertThat(jdbc.sql("select count(*) from studybot.spring_session where principal_name = :name")
			.param("name", principalName)
			.query(Long.class)
			.single()).isZero();
		assertThat(withSession(mvc.get().uri("/api/me"), login.session())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
