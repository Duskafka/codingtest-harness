package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * S05 만들 것 4: 규칙 변경 {@code PUT /api/studies/{id}/rules} (R2, OD-07, OD-08, T11).
 *
 * <p>시간은 Asia/Seoul이다. 스터디는 11/20 12:00(KST)에 만들어 1기가 11/21~12/18(28일), 2기가 12/19부터다.
 * <ul>
 * <li>다음 스터디 일부터: 하루 목표, 유예, 쉬는 요일, 알림 시각, 수동 인증 방식</li>
 * <li>다음 기수부터: 하루 기준 시각, 기수 길이, 기수당 면제권, 벌금(방식·금액)</li>
 * <li>1기 시작 전이면 모두 1기 시작일부터 (사람이 정함, 2026-11-24 "모두 1기 시작일부터")</li>
 * </ul>
 */
class StudyRuleChangeIntegrationTest extends StudyTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** 11/20 12:00 KST: 스터디를 만드는 시각. 오늘 11/20, 시작일 11/21. */
	private static final String CREATED_AT = "2026-11-20T03:00:00Z";

	/** 11/25 12:00 KST: 1기 중간. 다음 스터디 일 11/26. */
	private static final String MID_SEASON = "2026-11-25T03:00:00Z";

	/** 12/18 12:00 KST: 1기 마지막 날. 다음 스터디 일 12/19 = 2기 시작일. */
	private static final String LAST_DAY = "2026-12-18T03:00:00Z";

	private static final LocalDate SEASON_1 = LocalDate.parse("2026-11-21");

	private static final LocalDate NEXT_DAY = LocalDate.parse("2026-11-26");

	private static final LocalDate SEASON_2 = LocalDate.parse("2026-12-19");

	private TestUser owner;

	private long studyId;

	@BeforeEach
	void setUp() {
		setClock(CREATED_AT);
		owner = newUser("rule-owner");
		studyId = createStudy(owner, "GROUP");
	}

	// --- T11 ---

	@Test
	@DisplayName("T11: 다음 스터디 일부터 적용하는 항목(하루 목표)을 바꾸면 새 버전은 다음 스터디 일부터이고, 오늘의 규칙은 그대로다")
	void t11DailyItemAppliesFromNextStudyDay() {
		setClock(MID_SEASON);

		MvcTestResult result = putRules(owner, rules(Map.of("dailyGoal", 2)));

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(versions()).containsExactly(new Version(SEASON_1, 1, LocalTime.MIDNIGHT, 1000, 28),
				new Version(NEXT_DAY, 2, LocalTime.MIDNIGHT, 1000, 28));
		JsonNode detail = json(result);
		assertThat(detail.get("rule").get("effectiveFrom").asString()).isEqualTo("2026-11-21");
		assertThat(detail.get("rule").get("dailyGoal").asInt()).isEqualTo(1);
		assertThat(detail.get("upcomingRules")).hasSize(1);
		assertThat(detail.get("upcomingRules").get(0).get("effectiveFrom").asString()).isEqualTo("2026-11-26");
		assertThat(detail.get("upcomingRules").get(0).get("dailyGoal").asInt()).isEqualTo(2);
	}

	@Test
	@DisplayName("T11: 다음 기수부터 적용하는 항목(벌금)을 바꾸면 새 버전은 다음 기수 시작일(1기 끝 + 1)부터다")
	void t11SeasonItemAppliesFromNextSeason() {
		setClock(MID_SEASON);

		assertThat(putRules(owner, rules(Map.of("penaltyType", "FINE", "fineAmount", 2000)))).hasStatus(HttpStatus.OK);

		assertThat(versions()).containsExactly(new Version(SEASON_1, 1, LocalTime.MIDNIGHT, 1000, 28),
				new Version(SEASON_2, 1, LocalTime.MIDNIGHT, 2000, 28));
	}

	@Test
	@DisplayName("T11: 두 종류를 함께 바꾸면 다음 스터디 일 버전(다음 날 항목만 새 값)과 다음 기수 버전(모두 새 값)이 생긴다")
	void t11BothKindsMakeTwoVersions() {
		setClock(MID_SEASON);

		putRules(owner, rules(Map.of("dailyGoal", 2, "fineAmount", 2000, "seasonLengthDays", 14)));

		assertThat(versions()).containsExactly(new Version(SEASON_1, 1, LocalTime.MIDNIGHT, 1000, 28),
				new Version(NEXT_DAY, 2, LocalTime.MIDNIGHT, 1000, 28),
				new Version(SEASON_2, 2, LocalTime.MIDNIGHT, 2000, 14));
	}

	static Stream<Arguments> everyRuleItem() {
		return Stream.of(arguments("dailyGoal", 2, "daily_goal", "2", true),
				arguments("graceMinutes", 60, "grace_minutes", "60", true),
				arguments("restDays", List.of("SATURDAY"), "rest_days", "SATURDAY", true),
				arguments("reminderOffsetMinutes", 60, "reminder_offset_minutes", "60", true),
				arguments("selfReportMode", "APPROVAL_REQUIRED", "self_report_mode", "APPROVAL_REQUIRED", true),
				arguments("dayBoundary", "06:00", "day_boundary", "06:00:00", false),
				arguments("seasonLengthDays", 14, "season_length_days", "14", false),
				arguments("exemptionsPerSeason", 2, "exemptions_per_season", "2", false),
				arguments("penaltyType", "FINE", "penalty_type", "FINE", false),
				arguments("fineAmount", 2000, "fine_amount", "2000", false));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("everyRuleItem")
	@DisplayName("T11: 열 항목마다 R2의 적용 시점대로 다음 스터디 일 또는 다음 기수 시작일의 버전에만 들어간다")
	void t11EveryItemLandsInItsVersion(String field, Object value, String column, String stored, boolean nextDayItem) {
		setClock(MID_SEASON);
		Map<String, String> before = rows().getFirst();

		assertThat(putRules(owner, rules(Map.of(field, value)))).hasStatus(HttpStatus.OK);

		List<Map<String, String>> rows = rows();
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0)).isEqualTo(before);
		Map<String, String> expected = new HashMap<>(before);
		expected.put("effective_from", (nextDayItem ? NEXT_DAY : SEASON_2).toString());
		expected.put(column, stored);
		assertThat(rows.get(1)).isEqualTo(expected);
	}

	@Test
	@DisplayName("T11: 열 항목을 모두 바꾸면 다음 스터디 일 버전은 다음 날 항목만 새 값이고 기수 항목은 지금 값, 다음 기수 버전은 모두 새 값이다")
	void t11AllItemsSplitIntoTwoVersions() {
		setClock(MID_SEASON);
		Map<String, String> before = rows().getFirst();

		putRules(owner, rules(Map.of("dailyGoal", 2, "graceMinutes", 60, "restDays", List.of("SATURDAY"),
				"reminderOffsetMinutes", 60, "selfReportMode", "APPROVAL_REQUIRED", "dayBoundary", "06:00",
				"seasonLengthDays", 14, "exemptionsPerSeason", 2, "penaltyType", "FINE", "fineAmount", 2000)));

		List<Map<String, String>> rows = rows();
		assertThat(rows).hasSize(3);
		assertThat(rows.get(1)).containsEntry("effective_from", NEXT_DAY.toString())
			.containsEntry("daily_goal", "2")
			.containsEntry("grace_minutes", "60")
			.containsEntry("rest_days", "SATURDAY")
			.containsEntry("reminder_offset_minutes", "60")
			.containsEntry("self_report_mode", "APPROVAL_REQUIRED")
			.containsEntry("day_boundary", before.get("day_boundary"))
			.containsEntry("season_length_days", before.get("season_length_days"))
			.containsEntry("exemptions_per_season", before.get("exemptions_per_season"))
			.containsEntry("penalty_type", before.get("penalty_type"))
			.containsEntry("fine_amount", before.get("fine_amount"));
		assertThat(rows.get(2)).containsEntry("effective_from", SEASON_2.toString())
			.containsEntry("daily_goal", "2")
			.containsEntry("grace_minutes", "60")
			.containsEntry("rest_days", "SATURDAY")
			.containsEntry("reminder_offset_minutes", "60")
			.containsEntry("self_report_mode", "APPROVAL_REQUIRED")
			.containsEntry("day_boundary", "06:00:00")
			.containsEntry("season_length_days", "14")
			.containsEntry("exemptions_per_season", "2")
			.containsEntry("penalty_type", "FINE")
			.containsEntry("fine_amount", "2000");
	}

	@Test
	@DisplayName("S05: 1기 행이 끝난 뒤(다음 기수 행은 S12가 만듦)에는 기수 길이로 기수를 이어 세서 다음 기수 시작일을 정한다")
	void nextSeasonIsCountedOnWhenSeasonRowsEnded() {
		// 12/25 KST. 1기 행은 12/18에 끝났고, 이어 세면 2기는 12/19~2027-01-15, 3기는 2027-01-16부터다.
		setClock("2026-12-25T03:00:00Z");

		putRules(owner, rules(Map.of("fineAmount", 2000)));

		assertThat(versions()).containsExactly(new Version(SEASON_1, 1, LocalTime.MIDNIGHT, 1000, 28),
				new Version(LocalDate.parse("2027-01-16"), 1, LocalTime.MIDNIGHT, 2000, 28));
	}

	@Test
	@DisplayName("T11: 기수 마지막 날에 바꾸면 다음 스터디 일이 곧 다음 기수 시작일이라 버전 하나에 모두 들어간다")
	void lastDayOfSeasonMakesOneVersion() {
		setClock(LAST_DAY);

		putRules(owner, rules(Map.of("dailyGoal", 2, "fineAmount", 2000)));

		assertThat(versions()).containsExactly(new Version(SEASON_1, 1, LocalTime.MIDNIGHT, 1000, 28),
				new Version(SEASON_2, 2, LocalTime.MIDNIGHT, 2000, 28));
	}

	@Test
	@DisplayName("S05: 1기 시작 전에 바꾸면 모든 항목이 1기 시작일부터다 (버전 하나, 1기 끝 날짜도 새 기수 길이로)")
	void beforeFirstSeasonEverythingAppliesFromStart() {
		MvcTestResult result = putRules(owner,
				rules(Map.of("dailyGoal", 3, "dayBoundary", "06:00", "fineAmount", 2000, "seasonLengthDays", 14)));

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(versions()).containsExactly(new Version(SEASON_1, 3, LocalTime.of(6, 0), 2000, 14));
		JsonNode detail = json(result);
		assertThat(detail.get("season").get("startDay").asString()).isEqualTo("2026-11-21");
		assertThat(detail.get("season").get("endDay").asString()).isEqualTo("2026-12-04");
		assertThat(detail.get("upcomingRules")).isEmpty();
	}

	@Test
	@DisplayName("S05: 1기 시작 직전에 기준 시각을 앞당겨 1기 첫날이 이미 시작된 것이 되면 409이고 아무것도 바뀌지 않는다 (사람이 정함, 2026-11-24 \"거절 (409)\")")
	void boundaryChangeThatWouldStartFirstDayIsRejected() {
		// 11/21 03:00 KST에 기준 06:00으로 만든다: 오늘은 11/20, 1기 첫날 11/21은 06:00에 시작한다.
		setClock("2026-11-20T18:00:00Z");
		TestUser earlyOwner = newUser("dawn-owner");
		long early = createStudyWithBody(earlyOwner,
				"{\"name\": \"새벽 스터디\", \"mode\": \"GROUP\", \"rule\": {\"dayBoundary\": \"06:00\"}}");
		// 11/21 04:00 KST에 00:00으로 바꾸면 1기 첫날이 4시간 전에 시작한 셈이 된다.
		setClock("2026-11-20T19:00:00Z");

		MvcTestResult result = putJson(earlyOwner, "/api/studies/" + early + "/rules", rules(Map.of("dayBoundary", "00:00")));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(json(get(earlyOwner, "/api/studies/" + early)).get("rule").get("dayBoundary").asString())
			.isEqualTo("06:00");
	}

	@Test
	@DisplayName("S05: 다시 바꾸면 아직 적용 전인 예정 버전을 새 값으로 바꾼다 (같은 날 버전이 둘 생기지 않음)")
	void secondChangeReplacesPendingVersions() {
		setClock(MID_SEASON);
		putRules(owner, rules(Map.of("dailyGoal", 2, "fineAmount", 2000)));

		putRules(owner, rules(Map.of("dailyGoal", 3)));

		assertThat(versions()).containsExactly(new Version(SEASON_1, 1, LocalTime.MIDNIGHT, 1000, 28),
				new Version(NEXT_DAY, 3, LocalTime.MIDNIGHT, 1000, 28));
	}

	@Test
	@DisplayName("S05: 지금 규칙과 같은 값으로 바꾸면 예정된 변경이 없어진다")
	void sameAsCurrentCancelsPendingChanges() {
		setClock(MID_SEASON);
		putRules(owner, rules(Map.of("dailyGoal", 2, "fineAmount", 2000)));

		MvcTestResult result = putRules(owner, rules(Map.of()));

		assertThat(versions()).containsExactly(new Version(SEASON_1, 1, LocalTime.MIDNIGHT, 1000, 28));
		assertThat(json(result).get("upcomingRules")).isEmpty();
	}

	@Test
	@DisplayName("S05: 멤버도 상세에서 현재 규칙과 예정된 변경을 본다")
	void memberSeesUpcomingRules() {
		TestUser member = newUser("rule-member");
		join(owner, studyId, member);
		setClock(MID_SEASON);
		putRules(owner, rules(Map.of("penaltyType", "FINE", "fineAmount", 2000)));

		JsonNode detail = json(get(member, "/api/studies/" + studyId));

		assertThat(detail.get("rule").get("fineAmount").asInt()).isEqualTo(1000);
		assertThat(detail.get("upcomingRules")).hasSize(1);
		assertThat(detail.get("upcomingRules").get(0).get("effectiveFrom").asString()).isEqualTo("2026-12-19");
		assertThat(detail.get("upcomingRules").get(0).get("penaltyType").asString()).isEqualTo("FINE");
	}

	// --- 버전에 걸친 날 계산 ---

	@Test
	@DisplayName("S05: 기준 시각을 06:00에서 00:00으로 바꾼 뒤 2기 첫날 새벽 3시는 아직 1기 마지막 날이라, 가입하면 2기 첫날부터 판정한다")
	void joiningAtBoundaryChangeUsesVersionedCalendar() {
		TestUser earlyOwner = newUser("early-owner");
		long early = createStudyWithBody(earlyOwner,
				"{\"name\": \"새벽 스터디\", \"mode\": \"GROUP\", \"rule\": {\"dayBoundary\": \"06:00\"}}");
		setClock(MID_SEASON);
		assertThat(putJson(earlyOwner, "/api/studies/" + early + "/rules", rules(Map.of("dayBoundary", "00:00"))))
			.hasStatus(HttpStatus.OK);
		// 12/19 03:00 KST. 1기 마지막 날(12/18)은 옛 기준 06:00까지라 아직 12/18이다.
		setClock("2026-12-18T18:00:00Z");
		String token = createInvitation(earlyOwner, early, null).get("token").asString();
		TestUser member = newUser("dawn-member");
		// 공개 미리보기도 버전이 둘일 때 현재 규칙과 예정된 변경을 보여 준다 (사람이 정함, 2026-11-24 "예정 변경도 보여 줌").
		JsonNode preview = json(mvc.get().uri("/api/invitations/{token}", token).exchange());
		assertThat(preview.get("rule").get("dayBoundary").asString()).isEqualTo("06:00");
		assertThat(preview.get("upcomingRules")).hasSize(1);
		assertThat(preview.get("upcomingRules").get(0).get("effectiveFrom").asString()).isEqualTo("2026-12-19");
		assertThat(preview.get("upcomingRules").get(0).get("rule").get("dayBoundary").asString()).isEqualTo("00:00");

		MvcTestResult accepted = accept(member, token);

		assertThat(accepted).hasStatus(HttpStatus.OK);
		assertThat(json(accepted).get("judgedFrom").asString()).isEqualTo("2026-12-19");
		assertThat(json(get(earlyOwner, "/api/studies/" + early)).get("rule").get("dayBoundary").asString())
			.isEqualTo("06:00");
	}

	// --- 권한과 검증 ---

	@Test
	@DisplayName("S05: 방장이 아닌 멤버는 규칙을 바꿀 수 없다 (403)")
	void memberCannotChangeRules() {
		TestUser member = newUser("rule-member");
		join(owner, studyId, member);

		assertThat(putRules(member, rules(Map.of("dailyGoal", 2)))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(versions()).hasSize(1);
	}

	@Test
	@DisplayName("T19: 멤버가 아닌 사람이 규칙을 바꾸거나, 다른 스터디의 ID로 요청하면 404다")
	void t19OutsiderCannotChangeRules() {
		TestUser outsider = newUser("outsider");
		long otherStudy = createStudy(outsider, "GROUP");

		assertThat(putRules(outsider, rules(Map.of("dailyGoal", 2)))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(putJson(owner, "/api/studies/" + otherStudy + "/rules", rules(Map.of("dailyGoal", 2))))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(versions()).hasSize(1);
	}

	@Test
	@DisplayName("S05: 규칙 전체를 받는다. 항목이 빠지거나 R1 범위 밖이면 400이고 아무것도 바뀌지 않는다")
	void incompleteOrOutOfRangeRulesAreRejected() {
		setClock(MID_SEASON);
		ObjectNode missing = (ObjectNode) JSON.readTree(rules(Map.of()));
		missing.remove("graceMinutes");

		assertThat(putRules(owner, JSON.writeValueAsString(missing))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(putRules(owner, rules(Map.of("dailyGoal", 6)))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(putRules(owner, rules(Map.of("dayBoundary", "07:00")))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(putRules(owner, rules(Map.of("seasonLengthDays", 6)))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(versions()).hasSize(1);
	}

	private MvcTestResult putRules(TestUser user, String body) {
		return putJson(user, "/api/studies/" + studyId + "/rules", body);
	}

	/** R1 기본값으로 채운 규칙 전체에 바꿀 값만 덮어쓴다. */
	private static String rules(Map<String, Object> overrides) {
		ObjectNode node = JSON.createObjectNode();
		node.put("dailyGoal", 1);
		node.put("dayBoundary", "00:00");
		node.put("graceMinutes", 30);
		node.set("restDays", JSON.createArrayNode());
		node.put("penaltyType", "NONE");
		node.put("fineAmount", 1000);
		node.put("exemptionsPerSeason", 4);
		node.put("reminderOffsetMinutes", 120);
		node.put("selfReportMode", "IMMEDIATE");
		node.put("seasonLengthDays", 28);
		overrides.forEach((key, value) -> node.set(key, JSON.valueToTree(value)));
		return JSON.writeValueAsString(node);
	}

	/** 이 스터디의 규칙 버전 행 전체 (값은 문자열로). 적용 시작일 순서. */
	private List<Map<String, String>> rows() {
		return jdbc.sql("""
				select effective_from::text, daily_goal::text, day_boundary::text, grace_minutes::text, rest_days,
					penalty_type, fine_amount::text, exemptions_per_season::text, reminder_offset_minutes::text,
					self_report_mode, season_length_days::text
				from studybot.study_rules where study_id = :studyId order by effective_from
				""")
			.param("studyId", studyId)
			.query((rs, rowNum) -> {
				Map<String, String> row = new HashMap<>();
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					row.put(rs.getMetaData().getColumnLabel(i), rs.getString(i));
				}
				return row;
			})
			.list();
	}

	private List<Version> versions() {
		return jdbc.sql("""
				select effective_from, daily_goal, day_boundary, fine_amount, season_length_days
				from studybot.study_rules where study_id = :studyId order by effective_from
				""")
			.param("studyId", studyId)
			.query((rs, rowNum) -> new Version(rs.getObject("effective_from", LocalDate.class), rs.getInt("daily_goal"),
					rs.getObject("day_boundary", LocalTime.class), rs.getInt("fine_amount"),
					rs.getInt("season_length_days")))
			.list();
	}

	/** 규칙 버전 행에서 테스트가 보는 값. */
	record Version(LocalDate effectiveFrom, int dailyGoal, LocalTime dayBoundary, int fineAmount, int seasonLengthDays) {
	}

}
