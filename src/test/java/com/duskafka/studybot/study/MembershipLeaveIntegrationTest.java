package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 스터디 탈퇴 {@code DELETE /api/studies/{id}/members/me} (S05 만들 것 7, R14, 5.16 '스터디 탈퇴'). */
class MembershipLeaveIntegrationTest extends StudyTestSupport {

	@BeforeEach
	void setUp() {
		setClock("2026-11-20T06:00:00Z");
	}

	@Test
	@DisplayName("S05: 멤버가 탈퇴하면 left_at이 기록되고 행은 남는다")
	void memberLeaves() {
		TestUser owner = newUser("leave-owner");
		TestUser member = newUser("leave-member");
		long studyId = createStudy(owner, "GROUP");
		join(owner, studyId, member);
		setClock("2026-11-22T12:00:00Z");

		MvcTestResult result = delete(member, "/api/studies/" + studyId + "/members/me");

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		Map<String, Object> row = membershipsOf(studyId, member).getFirst();
		assertThat(((java.sql.Timestamp) row.get("left_at")).toInstant()).isEqualTo(Instant.parse("2026-11-22T12:00:00Z"));
		assertThat(activeMemberCount(studyId)).isEqualTo(1);
	}

	@Test
	@DisplayName("S05: 탈퇴한 뒤에는 스터디를 볼 수 없고, 다시 탈퇴할 수도 없다 (404)")
	void leftMemberLosesAccess() {
		TestUser owner = newUser("left-owner");
		TestUser member = newUser("left-member");
		long studyId = createStudy(owner, "GROUP");
		join(owner, studyId, member);
		assertThat(delete(member, "/api/studies/" + studyId + "/members/me")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(get(member, "/api/studies/" + studyId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(delete(member, "/api/studies/" + studyId + "/members/me")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("S05 (R14): 방장은 위임하기 전에는 탈퇴할 수 없다 (409)")
	void ownerCannotLeaveBeforeDelegating() {
		TestUser owner = newUser("stuck-owner");
		TestUser member = newUser("stuck-member");
		long studyId = createStudy(owner, "GROUP");
		join(owner, studyId, member);

		MvcTestResult result = delete(owner, "/api/studies/" + studyId + "/members/me");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(membershipsOf(studyId, owner).getFirst().get("left_at")).isNull();
	}

	@Test
	@DisplayName("S05 (R14): 위임한 뒤에는 옛 방장도 탈퇴할 수 있다")
	void formerOwnerCanLeaveAfterDelegating() {
		TestUser owner = newUser("former-owner");
		TestUser member = newUser("former-member");
		long studyId = createStudy(owner, "GROUP");
		join(owner, studyId, member);
		assertThat(patchJson(owner, "/api/studies/" + studyId, "{\"ownerUserId\": " + member.id() + "}"))
			.hasStatus(HttpStatus.OK);

		assertThat(delete(owner, "/api/studies/" + studyId + "/members/me")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(activeMemberCount(studyId)).isEqualTo(1);
	}

}
