package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
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
 * 스터디 만들기 {@code POST /api/studies} (S05 만들 것 1·8, 5.1 처리 순서 1, R1, R15).
 *
 * <p>시작일 규칙은 사람이 정했다 (2026-11-23, S05 세션): 기본값은 다음 스터디 일, 고를 수 있는 범위는
 * 다음 스터디 일부터 오늘 스터디 일 + 30일까지. 규칙 v1의 effective_from, 1기 시작일, 방장의 judged_from이 시작일이다.
 */
class StudyCreationIntegrationTest extends StudyTestSupport {

	/** 2026-11-20 15:00 KST. 서울 기준 오늘 스터디 일은 11/20, 다음 스터디 일은 11/21. */
	private static final String NOV_20_AFTERNOON_KST = "2026-11-20T06:00:00Z";

	@BeforeEach
	void setUp() {
		setClock(NOV_20_AFTERNOON_KST);
	}

	@Test
	@DisplayName("S05: 스터디를 만들면 규칙 v1, 1기, 방장 멤버십이 함께 생기고 빠진 규칙은 R1 기본값이다")
	void createStoresRuleSeasonAndOwnerTogetherWithDefaults() {
		TestUser owner = newUser("creator");

		MvcTestResult result = postJson(owner, "/api/studies", "{\"name\": \"  알고리즘 아침반  \", \"mode\": \"GROUP\"}");

		assertThat(result).hasStatus(HttpStatus.CREATED).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
		long studyId = json(result).get("id").asLong();
		assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/studies/" + studyId);

		Map<String, Object> study = jdbc.sql("select * from studybot.studies where id = :id")
			.param("id", studyId)
			.query()
			.singleRow();
		assertThat(study).containsEntry("name", "알고리즘 아침반")
			.containsEntry("mode", "GROUP")
			.containsEntry("owner_id", owner.id())
			.containsEntry("timezone", "Asia/Seoul")
			.containsEntry("status", "ACTIVE");

		List<Map<String, Object>> rules = jdbc.sql("select * from studybot.study_rules where study_id = :id")
			.param("id", studyId)
			.query()
			.listOfRows();
		assertThat(rules).hasSize(1);
		Map<String, Object> rule = rules.getFirst();
		assertThat(((java.sql.Date) rule.get("effective_from")).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 21));
		assertThat(rule).containsEntry("daily_goal", 1)
			.containsEntry("grace_minutes", 30)
			.containsEntry("rest_days", "")
			.containsEntry("penalty_type", "NONE")
			.containsEntry("fine_amount", 1000)
			.containsEntry("exemptions_per_season", 4)
			.containsEntry("reminder_offset_minutes", 120)
			.containsEntry("self_report_mode", "IMMEDIATE")
			.containsEntry("season_length_days", 28);
		assertThat(((java.sql.Time) rule.get("day_boundary")).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
		assertThat(rule.get("deposit_amount")).isNull();

		List<Map<String, Object>> seasons = jdbc.sql("select * from studybot.seasons where study_id = :id")
			.param("id", studyId)
			.query()
			.listOfRows();
		assertThat(seasons).hasSize(1);
		assertThat(seasons.getFirst()).containsEntry("seq", 1).containsEntry("status", "SCHEDULED");
		assertThat(((java.sql.Date) seasons.getFirst().get("start_day")).toLocalDate())
			.isEqualTo(LocalDate.of(2026, 11, 21));
		assertThat(((java.sql.Date) seasons.getFirst().get("end_day")).toLocalDate())
			.as("28일 기수의 마지막 날은 시작일 + 27일")
			.isEqualTo(LocalDate.of(2026, 12, 18));

		List<Map<String, Object>> memberships = membershipsOf(studyId, owner);
		assertThat(memberships).hasSize(1);
		assertThat(memberships.getFirst()).containsEntry("role", "OWNER");
		assertThat(memberships.getFirst().get("left_at")).isNull();
		assertThat(((java.sql.Date) memberships.getFirst().get("judged_from")).toLocalDate())
			.as("방장은 시작일부터 판정받는다")
			.isEqualTo(LocalDate.of(2026, 11, 21));
	}

	@Test
	@DisplayName("S05: 만들기 응답은 스터디 상세(내 역할, 현재 규칙, 기수)다")
	void createResponseIsStudyDetail() {
		TestUser owner = newUser("creator-response");

		MvcTestResult result = postJson(owner, "/api/studies", """
				{"name": "주말반", "mode": "GROUP", "timezone": "America/New_York", "startDate": "2026-11-23",
				 "rule": {"dailyGoal": 2, "dayBoundary": "05:30", "graceMinutes": 0, "restDays": ["SUNDAY", "SATURDAY"],
				          "penaltyType": "FINE", "fineAmount": 2000, "exemptionsPerSeason": 0,
				          "reminderOffsetMinutes": 30, "selfReportMode": "APPROVAL_REQUIRED", "seasonLengthDays": 7}}
				""");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		JsonNode body = json(result);
		assertThat(body.get("name").asString()).isEqualTo("주말반");
		assertThat(body.get("mode").asString()).isEqualTo("GROUP");
		assertThat(body.get("timezone").asString()).isEqualTo("America/New_York");
		assertThat(body.get("myRole").asString()).isEqualTo("OWNER");
		assertThat(body.get("ownerUserId").asLong()).isEqualTo(owner.id());
		assertThat(body.get("memberCount").asInt()).isEqualTo(1);
		assertThat(body.get("capacity").asInt()).isEqualTo(30);
		JsonNode rule = body.get("rule");
		assertThat(rule.get("effectiveFrom").asString()).isEqualTo("2026-11-23");
		assertThat(rule.get("dailyGoal").asInt()).isEqualTo(2);
		assertThat(rule.get("dayBoundary").asString()).isEqualTo("05:30");
		assertThat(rule.get("graceMinutes").asInt()).isZero();
		assertThat(rule.get("restDays").valueStream().map(JsonNode::asString).toList())
			.as("쉬는 요일은 월요일부터 순서대로")
			.containsExactly("SATURDAY", "SUNDAY");
		assertThat(rule.get("penaltyType").asString()).isEqualTo("FINE");
		assertThat(rule.get("fineAmount").asInt()).isEqualTo(2000);
		assertThat(rule.get("exemptionsPerSeason").asInt()).isZero();
		assertThat(rule.get("reminderOffsetMinutes").asInt()).isEqualTo(30);
		assertThat(rule.get("selfReportMode").asString()).isEqualTo("APPROVAL_REQUIRED");
		assertThat(rule.get("seasonLengthDays").asInt()).isEqualTo(7);
		JsonNode season = body.get("season");
		assertThat(season.get("seq").asInt()).isEqualTo(1);
		assertThat(season.get("startDay").asString()).isEqualTo("2026-11-23");
		assertThat(season.get("endDay").asString()).isEqualTo("2026-11-29");
		assertThat(season.get("status").asString()).isEqualTo("SCHEDULED");
	}

	@Test
	@DisplayName("S05: 저장 중 하나라도 실패하면 스터디·규칙·기수·멤버십이 하나도 남지 않는다")
	void createIsAtomic() {
		TestUser owner = newUser("rollback");
		Map<String, Long> before = rowCounts();
		// 마지막에 저장하는 방장 멤버십 INSERT를 DB에서 실패시킨다. 앞서 저장한 스터디·규칙·기수도 함께 롤백돼야 한다.
		jdbc.sql("""
				create function studybot.s05_fail_membership() returns trigger language plpgsql
				as $$ begin raise exception 'S05 rollback test'; end $$
				""").update();
		jdbc.sql("create trigger s05_fail_membership before insert on studybot.memberships for each row when (new.user_id = "
				+ owner.id() + ") execute function studybot.s05_fail_membership()")
			.update();
		try {
			MvcTestResult result = postJson(owner, "/api/studies", "{\"name\": \"롤백\", \"mode\": \"GROUP\"}");

			assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		}
		finally {
			jdbc.sql("drop trigger s05_fail_membership on studybot.memberships").update();
			jdbc.sql("drop function studybot.s05_fail_membership()").update();
		}

		assertThat(rowCounts()).isEqualTo(before);
		assertThat(jdbc.sql("select count(*) from studybot.studies where owner_id = :ownerId")
			.param("ownerId", owner.id())
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	@DisplayName("S05: 시작일 기본값은 다음 스터디 일이고, 기준 시각을 반영한다")
	void defaultStartDateIsNextStudyDay() {
		// 11/21 03:00 KST. 기준 06:00이면 아직 11/20 스터디 일이라 다음 스터디 일은 11/21, 기준 00:00이면 11/22다.
		setClock("2026-11-20T18:00:00Z");
		TestUser owner = newUser("default-start");

		JsonNode sixAm = json(postJson(owner, "/api/studies",
				"{\"name\": \"새벽반\", \"mode\": \"GROUP\", \"rule\": {\"dayBoundary\": \"06:00\"}}"));
		JsonNode midnight = json(postJson(owner, "/api/studies", "{\"name\": \"자정반\", \"mode\": \"GROUP\"}"));

		assertThat(sixAm.get("season").get("startDay").asString()).isEqualTo("2026-11-21");
		assertThat(sixAm.get("rule").get("effectiveFrom").asString()).isEqualTo("2026-11-21");
		assertThat(midnight.get("season").get("startDay").asString()).isEqualTo("2026-11-22");
	}

	@Test
	@DisplayName("S05: 시작일은 다음 스터디 일부터 오늘 + 30일까지 고를 수 있다")
	void startDateWithinRangeIsAccepted() {
		TestUser owner = newUser("start-range");

		JsonNode first = json(postJson(owner, "/api/studies",
				"{\"name\": \"내일\", \"mode\": \"GROUP\", \"startDate\": \"2026-11-21\"}"));
		JsonNode last = json(postJson(owner, "/api/studies",
				"{\"name\": \"한 달 뒤\", \"mode\": \"GROUP\", \"startDate\": \"2026-12-20\"}"));

		assertThat(first.get("season").get("startDay").asString()).isEqualTo("2026-11-21");
		assertThat(last.get("season").get("startDay").asString()).isEqualTo("2026-12-20");
	}

	@ParameterizedTest(name = "S05: 시작일 {0}은 범위 밖이라 400")
	@ValueSource(strings = { "2026-11-19", "2026-11-20", "2026-12-21" })
	void startDateOutOfRangeIsRejected(String startDate) {
		TestUser owner = newUser("start-out");

		MvcTestResult result = postJson(owner, "/api/studies",
				"{\"name\": \"범위 밖\", \"mode\": \"GROUP\", \"startDate\": \"" + startDate + "\"}");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@ParameterizedTest(name = "S05: R1 범위 밖이거나 잘못된 값 {0} → 400")
	@ValueSource(strings = { "{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"dailyGoal\": 0}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"dailyGoal\": 6}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"dayBoundary\": \"06:01\"}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"dayBoundary\": \"23:00\"}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"dayBoundary\": \"05:00:30\"}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"graceMinutes\": -1}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"graceMinutes\": 121}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"fineAmount\": 10001}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"exemptionsPerSeason\": 11}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"reminderOffsetMinutes\": 29}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"reminderOffsetMinutes\": 361}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"seasonLengthDays\": 6}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"seasonLengthDays\": 57}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"rule\": {\"penaltyType\": \"JAIL\"}}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"timezone\": \"Mars/Phobos\"}",
			"{\"name\": \"x\", \"mode\": \"GROUP\", \"timezone\": \"+09:00\"}",
			"{\"name\": \"   \", \"mode\": \"GROUP\"}",
			"{\"name\": \"123456789012345678901234567890123456789012345678901\", \"mode\": \"GROUP\"}",
			"{\"name\": \"x\"}", "{\"name\": \"x\", \"mode\": \"DUO\"}" })
	void invalidValuesAreRejected(String body) {
		TestUser owner = newUser("invalid");

		MvcTestResult result = postJson(owner, "/api/studies", body);

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(jdbc.sql("select count(*) from studybot.studies where owner_id = :ownerId")
			.param("ownerId", owner.id())
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	@DisplayName("S05: 혼자 쓰기 스터디를 만들 수 있고, 벌금 방식 기본값은 '없음'이다 (R15)")
	void soloStudyDefaultsToNoPenalty() {
		TestUser owner = newUser("solo");

		MvcTestResult result = postJson(owner, "/api/studies", "{\"name\": \"혼자\", \"mode\": \"SOLO\"}");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		JsonNode body = json(result);
		assertThat(body.get("mode").asString()).isEqualTo("SOLO");
		assertThat(body.get("rule").get("penaltyType").asString()).isEqualTo("NONE");
		assertThat(body.get("memberCount").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("S05: 로그인하지 않으면 스터디를 만들 수 없다 (401)")
	void anonymousCannotCreate() {
		MvcTestResult result = withCsrf(mvc.post().uri("/api/studies")).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"익명\", \"mode\": \"GROUP\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private Map<String, Long> rowCounts() {
		return Map.of("studies", count("studies"), "study_rules", count("study_rules"), "seasons", count("seasons"),
				"memberships", count("memberships"));
	}

	private long count(String table) {
		return jdbc.sql("select count(*) from studybot." + table).query(Long.class).single();
	}

}
