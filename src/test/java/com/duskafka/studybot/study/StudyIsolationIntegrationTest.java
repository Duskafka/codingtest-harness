package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/**
 * T19: 다른 스터디의 ID로 요청하면 403 또는 404 (testing.md 8.2, api.md 4.5 '조회 쿼리도 스터디 ID로 범위를 묶어요').
 * S05에서 만든 엔드포인트 중 경로에 스터디 ID가 있는 것을 모두 본다. 초대 미리보기·수락은 경로에 스터디 ID가 없고,
 * 토큰을 가진 사람만 쓸 수 있다.
 */
class StudyIsolationIntegrationTest extends StudyTestSupport {

	private TestUser alice;

	private TestUser bob;

	private long aliceStudy;

	private long bobStudy;

	private long bobInvitation;

	@BeforeEach
	void setUp() {
		setClock("2026-11-20T06:00:00Z");
		alice = newUser("iso-alice");
		bob = newUser("iso-bob");
		aliceStudy = createStudy(alice, "GROUP");
		bobStudy = createStudy(bob, "GROUP");
		bobInvitation = createInvitation(bob, bobStudy, null).get("id").asLong();
	}

	@Test
	@DisplayName("S05 (T19): 다른 스터디의 상세 → 404")
	void detailOfOtherStudy() {
		assertDenied(get(alice, "/api/studies/" + bobStudy));
	}

	@Test
	@DisplayName("S05 (T19): 다른 스터디의 이름 변경·위임 → 404이고 바뀌지 않는다")
	void patchOtherStudy() {
		assertDenied(patchJson(alice, "/api/studies/" + bobStudy, "{\"name\": \"탈취\"}"));
		assertDenied(patchJson(alice, "/api/studies/" + bobStudy, "{\"ownerUserId\": " + alice.id() + "}"));

		JsonNode bobView = json(get(bob, "/api/studies/" + bobStudy));
		assertThat(bobView.get("name").asString()).isEqualTo("S05 스터디");
		assertThat(bobView.get("ownerUserId").asLong()).isEqualTo(bob.id());
	}

	@Test
	@DisplayName("S05 (T19): 다른 스터디의 초대 만들기 → 404")
	void inviteToOtherStudy() {
		long before = invitationCount(bobStudy);

		assertDenied(postJson(alice, "/api/studies/" + bobStudy + "/invitations", "{}"));
		assertThat(invitationCount(bobStudy)).isEqualTo(before);
	}

	@Test
	@DisplayName("S05 (T19): 다른 스터디의 초대 철회 → 404. 내 스터디 ID에 남의 초대 ID를 붙여도 404")
	void revokeOtherStudyInvitation() {
		assertDenied(delete(alice, "/api/studies/" + bobStudy + "/invitations/" + bobInvitation));
		assertDenied(delete(alice, "/api/studies/" + aliceStudy + "/invitations/" + bobInvitation));

		assertThat(jdbc.sql("select revoked_at is null from studybot.invitations where id = :id")
			.param("id", bobInvitation)
			.query(Boolean.class)
			.single()).as("밥의 초대는 철회되지 않았다").isTrue();
	}

	@Test
	@DisplayName("S05 (T19): 다른 스터디에서 탈퇴 → 404이고 그 스터디 멤버십은 그대로다")
	void leaveOtherStudy() {
		assertDenied(delete(alice, "/api/studies/" + bobStudy + "/members/me"));

		assertThat(activeMemberCount(bobStudy)).isEqualTo(1);
		assertThat(membershipsOf(aliceStudy, alice).getFirst().get("left_at")).isNull();
	}

	@Test
	@DisplayName("S05 (T19): 내 스터디 목록에는 다른 스터디가 없다")
	void listExcludesOtherStudy() {
		List<Long> ids = json(get(alice, "/api/studies")).get("content")
			.valueStream()
			.map(item -> item.get("id").asLong())
			.toList();

		assertThat(ids).contains(aliceStudy).doesNotContain(bobStudy);
	}

	private long invitationCount(long studyId) {
		return jdbc.sql("select count(*) from studybot.invitations where study_id = :id")
			.param("id", studyId)
			.query(Long.class)
			.single();
	}

	private static void assertDenied(MvcTestResult result) {
		assertThat(result.getResponse().getStatus()).as("다른 스터디 요청은 403 또는 404").isIn(403, 404);
		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

}
