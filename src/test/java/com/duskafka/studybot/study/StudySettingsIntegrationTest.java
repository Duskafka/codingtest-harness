package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** 이름 변경과 방장 위임 {@code PATCH /api/studies/{id}} (S05 만들 것 3, api.md 4.4·4.5 권한 표). */
class StudySettingsIntegrationTest extends StudyTestSupport {

	@BeforeEach
	void setUp() {
		setClock("2026-11-20T06:00:00Z");
	}

	@Test
	@DisplayName("S05: 방장은 스터디 이름을 바꿀 수 있다")
	void ownerRenames() {
		TestUser owner = newUser("rename-owner");
		long studyId = createStudy(owner, "GROUP");

		MvcTestResult result = patchJson(owner, "/api/studies/" + studyId, "{\"name\": \" 새 이름 \"}");

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(json(result).get("name").asString()).isEqualTo("새 이름");
		assertThat(jdbc.sql("select name from studybot.studies where id = :id")
			.param("id", studyId)
			.query(String.class)
			.single()).isEqualTo("새 이름");
	}

	@Test
	@DisplayName("S05: 방장이 멤버에게 위임하면 역할이 서로 바뀌고 스터디의 방장도 바뀐다")
	void ownerDelegatesToMember() {
		TestUser owner = newUser("delegate-owner");
		TestUser member = newUser("delegate-member");
		long studyId = createStudy(owner, "GROUP");
		join(owner, studyId, member);

		MvcTestResult result = patchJson(owner, "/api/studies/" + studyId, "{\"ownerUserId\": " + member.id() + "}");

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = json(result);
		assertThat(body.get("ownerUserId").asLong()).isEqualTo(member.id());
		assertThat(body.get("myRole").asString()).isEqualTo("MEMBER");
		assertThat(membershipsOf(studyId, owner).getFirst()).containsEntry("role", "MEMBER");
		assertThat(membershipsOf(studyId, member).getFirst()).containsEntry("role", "OWNER");
		assertThat(jdbc.sql("select owner_id from studybot.studies where id = :id")
			.param("id", studyId)
			.query(Long.class)
			.single()).isEqualTo(member.id());
		assertThat(json(get(member, "/api/studies/" + studyId)).get("myRole").asString()).isEqualTo("OWNER");
	}

	@Test
	@DisplayName("S05: 방장이 아닌 멤버는 이름을 바꾸거나 위임할 수 없다 (403)")
	void memberCannotChangeSettings() {
		TestUser owner = newUser("settings-owner");
		TestUser member = newUser("settings-member");
		long studyId = createStudy(owner, "GROUP");
		join(owner, studyId, member);

		MvcTestResult rename = patchJson(member, "/api/studies/" + studyId, "{\"name\": \"탈취\"}");
		MvcTestResult delegate = patchJson(member, "/api/studies/" + studyId, "{\"ownerUserId\": " + member.id() + "}");

		assertThat(rename).hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(delegate).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(membershipsOf(studyId, owner).getFirst()).containsEntry("role", "OWNER");
	}

	@Test
	@DisplayName("S05: 멤버가 아닌 사람이나 탈퇴한 사람에게는 위임할 수 없다 (400)")
	void cannotDelegateToNonMember() {
		TestUser owner = newUser("delegate-invalid-owner");
		TestUser stranger = newUser("delegate-stranger");
		TestUser leaver = newUser("delegate-leaver");
		long studyId = createStudy(owner, "GROUP");
		join(owner, studyId, leaver);
		assertThat(delete(leaver, "/api/studies/" + studyId + "/members/me")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(patchJson(owner, "/api/studies/" + studyId, "{\"ownerUserId\": " + stranger.id() + "}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(patchJson(owner, "/api/studies/" + studyId, "{\"ownerUserId\": " + leaver.id() + "}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(membershipsOf(studyId, owner).getFirst()).containsEntry("role", "OWNER");
	}

	@Test
	@DisplayName("S05: 바꿀 값이 없거나 이름이 규칙에 맞지 않으면 400")
	void invalidPatchIsRejected() {
		TestUser owner = newUser("patch-invalid");
		long studyId = createStudy(owner, "GROUP");

		assertThat(patchJson(owner, "/api/studies/" + studyId, "{}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(patchJson(owner, "/api/studies/" + studyId, "{\"name\": \"  \"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(patchJson(owner, "/api/studies/" + studyId,
				"{\"name\": \"123456789012345678901234567890123456789012345678901\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("S05: 스터디 시간대는 바꿀 수 없다 (5.1). PATCH에 timezone만 보내면 바꿀 값이 없어 400이고 그대로다")
	void timezoneCannotBeChanged() {
		TestUser owner = newUser("timezone-owner");
		long studyId = createStudy(owner, "GROUP");

		MvcTestResult result = patchJson(owner, "/api/studies/" + studyId, "{\"timezone\": \"America/New_York\"}");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(jdbc.sql("select timezone from studybot.studies where id = :id")
			.param("id", studyId)
			.query(String.class)
			.single()).isEqualTo("Asia/Seoul");
	}

}
