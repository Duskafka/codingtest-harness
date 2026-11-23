package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/**
 * 초대 만들기·철회·미리보기·수락 (S05 만들 것 5·6·8, 5.1, R14, T20).
 *
 * <p>철회 엔드포인트 {@code DELETE /api/studies/{id}/invitations/{invitationId}}는 api.md 4.4에 없다.
 * 사람이 만들기로 정했고 (2026-11-23, S05 세션), PR '스펙 변경 제안'에 올렸다.
 */
class InvitationIntegrationTest extends StudyTestSupport {

	/** 2026-11-20 15:00 KST. */
	private static final String NOV_20_AFTERNOON_KST = "2026-11-20T06:00:00Z";

	@BeforeEach
	void setUp() {
		setClock(NOV_20_AFTERNOON_KST);
	}

	@Test
	@DisplayName("S05: 초대 토큰은 만들 때만 응답에 주고, DB에는 토큰 원문 없이 SHA-256 해시만 남는다")
	void tokenIsShownOnceAndOnlyHashIsStored() {
		TestUser owner = newUser("invite-owner");
		long studyId = createStudy(owner, "GROUP");

		JsonNode invitation = createInvitation(owner, studyId, null);

		String token = invitation.get("token").asString();
		assertThat(token).as("32바이트를 base64url(패딩 없음)로 쓰면 43자").matches("[A-Za-z0-9_-]{43}");
		long invitationId = invitation.get("id").asLong();
		String row = jdbc.sql("select row_to_json(i)::text from studybot.invitations i where id = :id")
			.param("id", invitationId)
			.query(String.class)
			.single();
		assertThat(row).doesNotContain(token);
		Map<String, Object> stored = jdbc
			.sql("select encode(token_hash, 'hex') as hash, expires_at, max_uses, used_count, revoked_at, created_by "
					+ "from studybot.invitations where id = :id")
			.param("id", invitationId)
			.query()
			.singleRow();
		assertThat(stored).containsEntry("hash", sha256Hex(token))
			.containsEntry("max_uses", 30)
			.containsEntry("used_count", 0)
			.containsEntry("created_by", owner.id());
		assertThat(stored.get("revoked_at")).isNull();
		assertThat(((java.sql.Timestamp) stored.get("expires_at")).toInstant())
			.as("기본 7일 뒤 만료")
			.isEqualTo(Instant.parse(NOV_20_AFTERNOON_KST).plus(Duration.ofDays(7)));
		assertThat(invitation.get("expiresAt").asString()).isEqualTo("2026-11-27T06:00:00Z");
		assertThat(invitation.get("maxUses").asInt()).isEqualTo(30);
	}

	@Test
	@DisplayName("S05: 사용 횟수를 정해 초대를 만들 수 있다 (1~30)")
	void maxUsesCanBeChosen() {
		TestUser owner = newUser("invite-max-uses");
		long studyId = createStudy(owner, "GROUP");

		assertThat(createInvitation(owner, studyId, 3).get("maxUses").asInt()).isEqualTo(3);
		assertThat(postJson(owner, "/api/studies/" + studyId + "/invitations", "{\"maxUses\": 0}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(postJson(owner, "/api/studies/" + studyId + "/invitations", "{\"maxUses\": 31}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("S05 (R15): 혼자 쓰기 스터디에서는 초대를 만들 수 없다")
	void soloStudyCannotInvite() {
		TestUser owner = newUser("solo-owner");
		long studyId = createStudy(owner, "SOLO");

		MvcTestResult result = postJson(owner, "/api/studies/" + studyId + "/invitations", "{}");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(jdbc.sql("select count(*) from studybot.invitations where study_id = :id")
			.param("id", studyId)
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	@DisplayName("S05: 방장이 아닌 멤버는 초대를 만들거나 철회할 수 없다 (403)")
	void memberCannotManageInvitations() {
		TestUser owner = newUser("invite-perm-owner");
		TestUser member = newUser("invite-perm-member");
		long studyId = createStudy(owner, "GROUP");
		join(owner, studyId, member);
		long invitationId = createInvitation(owner, studyId, null).get("id").asLong();

		assertThat(postJson(member, "/api/studies/" + studyId + "/invitations", "{}")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(delete(member, "/api/studies/" + studyId + "/invitations/" + invitationId))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	@DisplayName("S05: 초대 미리보기는 로그인 없이 볼 수 있고, 가입 전에 규칙 요약을 보여 준다 (신원 정보는 없다)")
	void previewIsPublicAndShowsRuleSummary() {
		TestUser owner = newUser("preview-owner");
		long studyId = createStudyWithBody(owner, """
				{"name": "아침 알고리즘", "mode": "GROUP",
				 "rule": {"dailyGoal": 2, "penaltyType": "FINE", "fineAmount": 3000, "restDays": ["SUNDAY"]}}
				""");
		String token = createInvitation(owner, studyId, null).get("token").asString();

		MvcTestResult result = mvc.get().uri("/api/invitations/{token}", token).exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = json(result);
		assertThat(body.get("study").get("name").asString()).isEqualTo("아침 알고리즘");
		assertThat(body.get("study").get("timezone").asString()).isEqualTo("Asia/Seoul");
		JsonNode rule = body.get("rule");
		assertThat(rule.get("dailyGoal").asInt()).isEqualTo(2);
		assertThat(rule.get("dayBoundary").asString()).isEqualTo("00:00");
		assertThat(rule.get("graceMinutes").asInt()).isEqualTo(30);
		assertThat(rule.get("restDays").valueStream().map(JsonNode::asString).toList()).containsExactly("SUNDAY");
		assertThat(rule.get("penaltyType").asString()).isEqualTo("FINE");
		assertThat(rule.get("fineAmount").asInt()).isEqualTo(3000);
		assertThat(rule.get("exemptionsPerSeason").asInt()).isEqualTo(4);
		assertThat(rule.get("selfReportMode").asString()).isEqualTo("IMMEDIATE");
		assertThat(rule.get("seasonLengthDays").asInt()).isEqualTo(28);
		assertThat(body.get("season").get("startDay").asString()).isEqualTo("2026-11-21");
		assertThat(body.get("season").get("endDay").asString()).isEqualTo("2026-12-18");
		assertThat(body.get("expiresAt").asString()).isEqualTo("2026-11-27T06:00:00Z");
		assertThat(result).bodyJson().doesNotHavePath("$.study.id");
		assertThat(result).bodyJson().doesNotHavePath("$.study.ownerUserId");
		assertThat(result).bodyJson().doesNotHavePath("$.members");
		assertThat(result).bodyJson().doesNotHavePath("$.rule.reminderOffsetMinutes");
		assertThat(result).bodyText().doesNotContain(Long.toString(owner.githubId()));
	}

	@ParameterizedTest(name = "S05: 없는 토큰 {0}의 미리보기 → 404")
	@ValueSource(strings = { "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "short", "not+base64url=at.all..........................",
			"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" })
	void unknownTokenPreviewIsNotFound(String token) {
		MvcTestResult result = mvc.get().uri("/api/invitations/{token}", token).exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	@DisplayName("T20: 만료된 토큰은 미리보기와 수락이 거절된다 (만료 시각부터)")
	void expiredTokenIsRejected() {
		TestUser owner = newUser("expired-owner");
		TestUser joiner = newUser("expired-joiner");
		long studyId = createStudy(owner, "GROUP");
		String token = createInvitation(owner, studyId, null).get("token").asString();

		clock.advance(Duration.ofDays(7).minusSeconds(1));
		assertThat(mvc.get().uri("/api/invitations/{token}", token)).hasStatus(HttpStatus.OK);
		clock.advance(Duration.ofSeconds(1));

		assertThat(mvc.get().uri("/api/invitations/{token}", token)).hasStatus(HttpStatus.GONE);
		assertThat(accept(joiner, token)).hasStatus(HttpStatus.GONE).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(membershipsOf(studyId, joiner)).isEmpty();
	}

	@Test
	@DisplayName("T20: 방장이 철회한 토큰은 미리보기와 수락이 거절된다")
	void revokedTokenIsRejected() {
		TestUser owner = newUser("revoke-owner");
		TestUser joiner = newUser("revoke-joiner");
		long studyId = createStudy(owner, "GROUP");
		JsonNode invitation = createInvitation(owner, studyId, null);
		String token = invitation.get("token").asString();

		MvcTestResult revoke = delete(owner,
				"/api/studies/" + studyId + "/invitations/" + invitation.get("id").asLong());

		assertThat(revoke).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(jdbc.sql("select revoked_at is not null from studybot.invitations where id = :id")
			.param("id", invitation.get("id").asLong())
			.query(Boolean.class)
			.single()).isTrue();
		assertThat(mvc.get().uri("/api/invitations/{token}", token)).hasStatus(HttpStatus.GONE);
		assertThat(accept(joiner, token)).hasStatus(HttpStatus.GONE);
		assertThat(membershipsOf(studyId, joiner)).isEmpty();
	}

	@Test
	@DisplayName("T20: 사용 횟수를 다 쓴 토큰은 다른 사람의 수락이 거절된다")
	void usedUpTokenIsRejected() {
		TestUser owner = newUser("used-up-owner");
		TestUser first = newUser("used-up-first");
		TestUser second = newUser("used-up-second");
		long studyId = createStudy(owner, "GROUP");
		String token = createInvitation(owner, studyId, 1).get("token").asString();

		assertThat(accept(first, token)).hasStatus(HttpStatus.OK);

		assertThat(accept(second, token)).hasStatus(HttpStatus.GONE);
		assertThat(mvc.get().uri("/api/invitations/{token}", token)).hasStatus(HttpStatus.GONE);
		assertThat(membershipsOf(studyId, second)).isEmpty();
	}

	@Test
	@DisplayName("S05: 같은 사람이 같은 초대를 두 번 수락하면 두 번 다 성공 응답이고 멤버십은 하나다")
	void acceptIsIdempotent() {
		TestUser owner = newUser("idem-owner");
		TestUser joiner = newUser("idem-joiner");
		long studyId = createStudy(owner, "GROUP");
		JsonNode invitation = createInvitation(owner, studyId, 2);
		String token = invitation.get("token").asString();

		MvcTestResult first = accept(joiner, token);
		MvcTestResult second = accept(joiner, token);

		assertThat(first).hasStatus(HttpStatus.OK);
		assertThat(second).hasStatus(HttpStatus.OK);
		assertThat(json(second)).isEqualTo(json(first));
		assertThat(json(first).get("studyId").asLong()).isEqualTo(studyId);
		assertThat(json(first).get("myRole").asString()).isEqualTo("MEMBER");
		assertThat(membershipsOf(studyId, joiner)).hasSize(1);
		assertThat(jdbc.sql("select used_count from studybot.invitations where id = :id")
			.param("id", invitation.get("id").asLong())
			.query(Integer.class)
			.single()).as("이미 멤버면 사용 횟수를 쓰지 않는다").isEqualTo(1);
	}

	@Test
	@DisplayName("S05 (R14): 11/20에 가입하면 judged_from은 11/21이다 (기준 00:00)")
	void judgedFromIsNextStudyDay() {
		setClock("2026-11-19T03:00:00Z");
		TestUser owner = newUser("judged-owner");
		TestUser joiner = newUser("judged-joiner");
		long studyId = createStudy(owner, "GROUP");
		String token = createInvitation(owner, studyId, null).get("token").asString();
		setClock(NOV_20_AFTERNOON_KST);

		MvcTestResult result = accept(joiner, token);

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(json(result).get("judgedFrom").asString()).isEqualTo("2026-11-21");
		assertThat(judgedFromOf(studyId, joiner)).isEqualTo(LocalDate.of(2026, 11, 21));
	}

	@Test
	@DisplayName("S05 (R14): 기준 06:00 스터디에 11/21 03:00에 가입하면 아직 11/20 스터디 일이라 judged_from은 11/21이다")
	void judgedFromReflectsDayBoundary() {
		setClock("2026-11-19T03:00:00Z");
		TestUser owner = newUser("boundary-owner");
		TestUser joiner = newUser("boundary-joiner");
		long studyId = createStudyWithBody(owner,
				"{\"name\": \"새벽반\", \"mode\": \"GROUP\", \"rule\": {\"dayBoundary\": \"06:00\"}}");
		String token = createInvitation(owner, studyId, null).get("token").asString();
		setClock("2026-11-20T18:00:00Z");

		MvcTestResult result = accept(joiner, token);

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(json(result).get("judgedFrom").asString()).isEqualTo("2026-11-21");
	}

	@Test
	@DisplayName("S05 (R14): 스터디 시작 전에 가입해도 judged_from은 가입 다음 스터디 일이다 (시작 전 날은 1기 밖, R9·OD-07)")
	void judgedFromBeforeStudyStartIsStillNextStudyDay() {
		TestUser owner = newUser("early-owner");
		TestUser joiner = newUser("early-joiner");
		long studyId = createStudyWithBody(owner, "{\"name\": \"다음 주\", \"mode\": \"GROUP\", \"startDate\": \"2026-11-25\"}");
		String token = createInvitation(owner, studyId, null).get("token").asString();

		MvcTestResult result = accept(joiner, token);

		assertThat(json(result).get("judgedFrom").asString()).isEqualTo("2026-11-21");
	}

	@Test
	@DisplayName("S05: 탈퇴한 사람이 다시 가입할 수 있다 (탈퇴하지 않은 멤버십에만 걸리는 부분 UNIQUE)")
	void leftMemberCanRejoin() {
		TestUser owner = newUser("rejoin-owner");
		TestUser member = newUser("rejoin-member");
		long studyId = createStudy(owner, "GROUP");
		join(owner, studyId, member);
		assertThat(delete(member, "/api/studies/" + studyId + "/members/me")).hasStatus(HttpStatus.NO_CONTENT);
		String token = createInvitation(owner, studyId, null).get("token").asString();

		MvcTestResult result = accept(member, token);

		assertThat(result).hasStatus(HttpStatus.OK);
		List<Map<String, Object>> rows = membershipsOf(studyId, member);
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).get("left_at")).isNotNull();
		assertThat(rows.get(1).get("left_at")).isNull();
		assertThat(get(member, "/api/studies/" + studyId)).hasStatus(HttpStatus.OK);
	}

	@Test
	@DisplayName("S05: 로그인하지 않으면 초대를 수락할 수 없다 (401)")
	void anonymousCannotAccept() {
		TestUser owner = newUser("anon-accept-owner");
		long studyId = createStudy(owner, "GROUP");
		String token = createInvitation(owner, studyId, null).get("token").asString();

		assertThat(withCsrf(mvc.post().uri("/api/invitations/{token}/accept", token))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private LocalDate judgedFromOf(long studyId, TestUser user) {
		return jdbc
			.sql("select judged_from from studybot.memberships where study_id = :studyId and user_id = :userId and left_at is null")
			.param("studyId", studyId)
			.param("userId", user.id())
			.query(LocalDate.class)
			.single();
	}

	private static String sha256Hex(String token) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
