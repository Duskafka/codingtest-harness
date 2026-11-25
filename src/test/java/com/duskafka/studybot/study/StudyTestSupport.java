package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.auth.LoginUser;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * S05 통합 테스트의 공통 도우미.
 *
 * <ul>
 * <li>사용자는 DB에 바로 넣고, 로그인은 세션에 담기는 인증 주체({@link LoginUser})를 요청에 붙여 흉내 낸다.
 * GitHub 로그인 흐름 자체는 S04 테스트가 본다.</li>
 * <li>CSRF 토큰은 SPA처럼 XSRF-TOKEN 쿠키로 받아 X-XSRF-TOKEN 헤더로 돌려보낸다. Spring Security 테스트의 {@code csrf()}
 * 후처리기는 공유 컨텍스트의 CSRF 토큰 저장소를 바꿔 끼워 뒤 테스트를 망가뜨린다 (S04 인계 기록).</li>
 * <li>테스트끼리 DB를 공유하므로 사용자마다 다른 github_id를 쓴다.</li>
 * </ul>
 */
@IntegrationTest
abstract class StudyTestSupport {

	static final String XSRF_COOKIE = "XSRF-TOKEN";

	static final String XSRF_HEADER = "X-XSRF-TOKEN";

	/** S04 테스트(6101 등)와 겹치지 않는 github_id. */
	private static final AtomicLong NEXT_GITHUB_ID = new AtomicLong(5_000_000L);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected JdbcClient jdbc;

	@Autowired
	protected MutableClock clock;

	private @Nullable String csrfToken;

	/** 테스트용 사용자를 DB에 넣는다. */
	protected TestUser newUser(String login) {
		long githubId = NEXT_GITHUB_ID.incrementAndGet();
		long id = jdbc.sql("""
				insert into studybot.users (github_id, github_login, created_at)
				values (:githubId, :login, :createdAt) returning id
				""")
			.param("githubId", githubId)
			.param("login", login + "-" + githubId)
			.param("createdAt", java.sql.Timestamp.from(clock.instant()))
			.query(Long.class)
			.single();
		return new TestUser(id, githubId);
	}

	/** 이 사용자로 로그인한 요청. */
	protected static RequestPostProcessor as(TestUser user) {
		LoginUser principal = new LoginUser(user.id(), user.githubId());
		return authentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "github"));
	}

	/** 상태를 바꾸는 요청에 CSRF 쿠키와 헤더를 붙인다. */
	protected MockMvcRequestBuilder withCsrf(MockMvcRequestBuilder request) {
		String token = csrfToken();
		return request.cookie(new Cookie(XSRF_COOKIE, token)).header(XSRF_HEADER, token);
	}

	protected MvcTestResult postJson(TestUser user, String uri, String body) {
		return withCsrf(mvc.post().uri(uri)).with(as(user))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	protected MvcTestResult patchJson(TestUser user, String uri, String body) {
		return withCsrf(mvc.patch().uri(uri)).with(as(user))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	protected MvcTestResult putJson(TestUser user, String uri, String body) {
		return withCsrf(mvc.put().uri(uri)).with(as(user))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	protected MvcTestResult delete(TestUser user, String uri) {
		return withCsrf(mvc.delete().uri(uri)).with(as(user)).exchange();
	}

	protected MvcTestResult get(TestUser user, String uri) {
		return mvc.get().uri(uri).with(as(user)).exchange();
	}

	/** 스터디를 만들고 ID를 돌려준다. 시작일은 기본값(다음 스터디 일)이다. */
	protected long createStudy(TestUser owner, String mode) {
		return createStudyWithBody(owner, "{\"name\": \"S05 스터디\", \"mode\": \"" + mode + "\"}");
	}

	protected long createStudyWithBody(TestUser owner, String body) {
		MvcTestResult result = postJson(owner, "/api/studies", body);
		assertThat(result).as("스터디 만들기").hasStatus(HttpStatus.CREATED);
		return json(result).get("id").asLong();
	}

	/** 초대를 만들고 응답(id, token, expiresAt, maxUses)을 돌려준다. */
	protected JsonNode createInvitation(TestUser owner, long studyId, @Nullable Integer maxUses) {
		String body = maxUses == null ? "{}" : "{\"maxUses\": " + maxUses + "}";
		MvcTestResult result = postJson(owner, "/api/studies/" + studyId + "/invitations", body);
		assertThat(result).as("초대 만들기").hasStatus(HttpStatus.CREATED);
		return json(result);
	}

	protected MvcTestResult accept(TestUser user, String token) {
		return withCsrf(mvc.post().uri("/api/invitations/{token}/accept", token)).with(as(user)).exchange();
	}

	/** 초대를 거쳐 멤버로 가입시킨다. */
	protected void join(TestUser owner, long studyId, TestUser member) {
		String token = createInvitation(owner, studyId, null).get("token").asString();
		assertThat(accept(member, token)).as("초대 수락").hasStatus(HttpStatus.OK);
	}

	/** 탈퇴하지 않은 멤버십을 DB에 바로 넣는다 (정원 테스트 준비용). */
	protected void insertActiveMember(long studyId, TestUser user) {
		jdbc.sql("""
				insert into studybot.memberships (study_id, user_id, role, joined_at, judged_from)
				values (:studyId, :userId, 'MEMBER', :joinedAt, date '2026-01-02')
				""")
			.param("studyId", studyId)
			.param("userId", user.id())
			.param("joinedAt", java.sql.Timestamp.from(clock.instant()))
			.update();
	}

	protected long activeMemberCount(long studyId) {
		return jdbc.sql("select count(*) from studybot.memberships where study_id = :studyId and left_at is null")
			.param("studyId", studyId)
			.query(Long.class)
			.single();
	}

	protected List<Map<String, Object>> membershipsOf(long studyId, TestUser user) {
		return jdbc.sql("select * from studybot.memberships where study_id = :studyId and user_id = :userId order by id")
			.param("studyId", studyId)
			.param("userId", user.id())
			.query()
			.listOfRows();
	}

	protected void setClock(String instant) {
		clock.setInstant(Instant.parse(instant));
	}

	protected static JsonNode json(MvcTestResult result) {
		try {
			return JSON.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String csrfToken() {
		if (csrfToken == null) {
			MvcTestResult result = mvc.get().uri("/actuator/health").exchange();
			csrfToken = result.getResponse()
				.getHeaders("Set-Cookie")
				.stream()
				.filter(header -> header.startsWith(XSRF_COOKIE + "="))
				.map(header -> header.substring(XSRF_COOKIE.length() + 1).split(";", 2)[0])
				.filter(value -> !value.isEmpty())
				.findFirst()
				.orElseThrow(() -> new AssertionError("XSRF-TOKEN 쿠키가 없다"));
		}
		return csrfToken;
	}

	/**
	 * 테스트용 사용자.
	 * @param id {@code users.id}
	 * @param githubId {@code users.github_id}
	 */
	record TestUser(long id, long githubId) {
	}

}
