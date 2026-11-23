package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** 내 스터디 목록 {@code GET /api/studies}와 상세 {@code GET /api/studies/{id}} (S05 만들 것 2, api.md 4.4). */
class StudyQueryIntegrationTest extends StudyTestSupport {

	@BeforeEach
	void setUp() {
		setClock("2026-11-20T06:00:00Z");
	}

	@Test
	@DisplayName("S05: 목록에는 내가 멤버인 스터디만, 최근에 가입한 것부터 나온다 (탈퇴한 스터디는 빠진다)")
	void listShowsOnlyMyActiveStudies() {
		TestUser me = newUser("list-me");
		TestUser other = newUser("list-other");
		long mine = createStudy(me, "GROUP");
		long othersStudy = createStudy(other, "GROUP");
		long joinedThenLeft = createStudy(other, "GROUP");
		clock.advance(java.time.Duration.ofMinutes(1));
		join(other, othersStudy, me);
		join(other, joinedThenLeft, me);
		assertThat(delete(me, "/api/studies/" + joinedThenLeft + "/members/me")).hasStatus(HttpStatus.NO_CONTENT);
		long notMine = createStudy(other, "SOLO");

		MvcTestResult result = get(me, "/api/studies");

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = json(result);
		List<Long> ids = body.get("content").valueStream().map(item -> item.get("id").asLong()).toList();
		assertThat(ids).containsExactly(othersStudy, mine).doesNotContain(joinedThenLeft, notMine);
		JsonNode first = body.get("content").get(0);
		assertThat(first.get("name").asString()).isEqualTo("S05 스터디");
		assertThat(first.get("mode").asString()).isEqualTo("GROUP");
		assertThat(first.get("myRole").asString()).isEqualTo("MEMBER");
		assertThat(body.get("content").get(1).get("myRole").asString()).isEqualTo("OWNER");
		assertThat(body.get("page").get("totalElements").asLong()).isEqualTo(2);
	}

	@Test
	@DisplayName("S05: 목록은 페이지로 나눠 준다 (page, size)")
	void listIsPaginated() {
		TestUser me = newUser("page-me");
		long older = createStudy(me, "GROUP");
		clock.advance(java.time.Duration.ofMinutes(1));
		long newer = createStudy(me, "GROUP");

		JsonNode firstPage = json(get(me, "/api/studies?page=0&size=1"));
		JsonNode secondPage = json(get(me, "/api/studies?page=1&size=1"));

		assertThat(firstPage.get("content").get(0).get("id").asLong()).isEqualTo(newer);
		assertThat(secondPage.get("content").get(0).get("id").asLong()).isEqualTo(older);
		assertThat(firstPage.get("page").get("size").asInt()).isEqualTo(1);
		assertThat(firstPage.get("page").get("number").asInt()).isZero();
		assertThat(firstPage.get("page").get("totalElements").asLong()).isEqualTo(2);
		assertThat(firstPage.get("page").get("totalPages").asInt()).isEqualTo(2);
	}

	@ParameterizedTest(name = "S05: 목록의 잘못된 페이지 값 {0} → 400")
	@ValueSource(strings = { "page=-1", "size=0", "size=51" })
	void invalidPageParametersAreRejected(String query) {
		TestUser me = newUser("page-invalid");

		assertThat(get(me, "/api/studies?" + query)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("S05: 멤버는 스터디 상세(현재 규칙, 기수, 내 역할, 멤버 수)를 본다")
	void memberSeesDetail() {
		TestUser owner = newUser("detail-owner");
		TestUser member = newUser("detail-member");
		long studyId = createStudy(owner, "GROUP");
		join(owner, studyId, member);

		MvcTestResult result = get(member, "/api/studies/" + studyId);

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = json(result);
		assertThat(body.get("id").asLong()).isEqualTo(studyId);
		assertThat(body.get("myRole").asString()).isEqualTo("MEMBER");
		assertThat(body.get("ownerUserId").asLong()).isEqualTo(owner.id());
		assertThat(body.get("memberCount").asInt()).isEqualTo(2);
		assertThat(body.get("status").asString()).isEqualTo("ACTIVE");
		assertThat(body.get("rule").get("effectiveFrom").asString()).isEqualTo("2026-11-21");
		assertThat(body.get("season").get("startDay").asString()).isEqualTo("2026-11-21");
	}

	@Test
	@DisplayName("S05 (T19): 멤버가 아니면 스터디 상세를 볼 수 없다 (404)")
	void nonMemberCannotSeeDetail() {
		TestUser owner = newUser("detail-private-owner");
		TestUser stranger = newUser("detail-stranger");
		long studyId = createStudy(owner, "GROUP");

		assertThat(get(stranger, "/api/studies/" + studyId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(stranger, "/api/studies/999999999")).hasStatus(HttpStatus.NOT_FOUND);
	}

}
