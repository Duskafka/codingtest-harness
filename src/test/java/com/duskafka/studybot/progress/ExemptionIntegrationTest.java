package com.duskafka.studybot.progress;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import com.duskafka.studybot.progress.service.ExemptionService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 면제권 사용·취소와 남은 개수 (S11 만들 것 5·6, 수용 기준 T12·같은 날 두 번·쓸 수 없는 날·OD-09·T13 상태·T19).
 * {@code POST /api/studies/{id}/exemptions}, {@code DELETE /api/studies/{id}/exemptions/{studyDay}}.
 *
 * <p>OD-09 (사람이 정함, 2026-11-26): 면제권은 당일만. 지금 시각이 속한 스터디 일에만 쓰고 그날 기준 시각 전까지 취소한다.
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 기수당 면제권 4개, 1기 11-01~11-28 ({@link StudyDayRows#standardStudy}).
 * 지금은 11-20 10:00이다.
 */
@IntegrationTest
class ExemptionIntegrationTest {

	private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

	private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);

	private static final LocalDate NOV_4 = LocalDate.of(2026, 11, 4);

	private static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);

	private static final LocalDate NOV_19 = LocalDate.of(2026, 11, 19);

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private static final Instant NOW = seoul(NOV_20, 10, 0);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private ExemptionService exemptionService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private StudyDayRows rows;

	private TestApi api;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		api = new TestApi(mvc, jdbc);
		clock.setInstant(NOW);
	}

	private MvcTestResult use(long userId, long studyId, LocalDate studyDay) {
		return api.postJson(userId, "/api/studies/{id}/exemptions", "{\"studyDay\": \"%s\"}".formatted(studyDay),
				studyId);
	}

	private MvcTestResult cancel(long userId, long studyId, LocalDate studyDay) {
		return api.delete(userId, "/api/studies/{id}/exemptions/{studyDay}", studyId, studyDay);
	}

	private static String code(MvcTestResult result) {
		JsonNode code = TestApi.json(result).get("code");
		return code == null ? "(없음)" : code.asString();
	}

	private static int remaining(MvcTestResult result) {
		return TestApi.json(result).get("exemptionsRemaining").asInt();
	}

	private Instant exemptionUsedAt(long studyId, long userId, LocalDate day) {
		return rows.memberDay(studyId, userId, day).orElseThrow().exemptionUsedAt();
	}

	private boolean exemptionUsed(long studyId, long userId, LocalDate day) {
		return rows.memberDay(studyId, userId, day).map(row -> row.exemptionUsedAt() != null).orElse(false);
	}

	private JsonNode todayMember(long viewerId, long studyId, long userId) {
		MvcTestResult result = api.get(viewerId, "/api/studies/{id}/today", studyId);
		assertThat(result).hasStatus(HttpStatus.OK);
		for (JsonNode member : TestApi.json(result).get("members")) {
			if (member.get("userId").asLong() == userId) {
				return member;
			}
		}
		throw new AssertionError("멤버가 없다: " + userId);
	}

	// --- 사용 ---

	@Test
	@DisplayName("만들 것 5·6: 오늘 면제권을 쓰면 200, 멤버 일에 쓴 시각이 남고 남은 개수를 준다. 현황도 면제(면제권)와 남은 개수를 보여 준다")
	void useTodayRecordsAndReturnsRemaining() {
		long alice = rows.newUser("s11-use");
		TestStudy study = rows.standardStudy(alice);

		MvcTestResult result = use(alice, study.studyId(), NOV_20);

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = TestApi.json(result);
		assertThat(body.get("studyDay").asString()).isEqualTo("2026-11-20");
		assertThat(body.get("exemptionUsed").asBoolean()).isTrue();
		assertThat(body.get("exemptionsPerSeason").asInt()).isEqualTo(4);
		assertThat(body.get("exemptionsRemaining").asInt()).isEqualTo(3);
		assertThat(exemptionUsedAt(study.studyId(), alice, NOV_20)).isEqualTo(NOW);
		assertThat(rows.memberDay(study.studyId(), alice, NOV_20).orElseThrow().seasonId()).isEqualTo(study.seasonId());
		JsonNode member = todayMember(alice, study.studyId(), alice);
		assertThat(member.get("status").asString()).isEqualTo("EXEMPT");
		assertThat(member.get("exemptReason").asString()).isEqualTo("TICKET");
		assertThat(member.get("exemptionsRemaining").asInt()).isEqualTo(3);
	}

	@Test
	@DisplayName("수용 기준: 같은 날 면제권을 두 번 쓰면 한 번만 반영된다 (두 번째도 200, 쓴 시각·남은 개수 그대로)")
	void usingTwiceOnTheSameDayCountsOnce() {
		long alice = rows.newUser("s11-twice");
		TestStudy study = rows.standardStudy(alice);
		assertThat(use(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.OK);
		clock.setInstant(seoul(NOV_20, 11, 0));

		MvcTestResult again = use(alice, study.studyId(), NOV_20);

		assertThat(again).hasStatus(HttpStatus.OK);
		assertThat(remaining(again)).isEqualTo(3);
		assertThat(exemptionUsedAt(study.studyId(), alice, NOV_20)).isEqualTo(NOW);
	}

	@Test
	@DisplayName("수용 기준: 마지막 면제권을 쓴 날 다시 보내도 200이다 (한도 409가 아님). 쓴 뒤에 목표를 채웠어도 같다")
	void usingAgainAfterLastTicketIsStillOk() {
		long alice = rows.newUser("s11-twice-last");
		TestStudy study = rows.standardStudy(alice);
		rows.setExemptionsPerSeasonOfStudy(study.studyId(), 1);
		assertThat(remaining(use(alice, study.studyId(), NOV_20))).isZero();

		MvcTestResult again = use(alice, study.studyId(), NOV_20);
		assertThat(again).hasStatus(HttpStatus.OK);
		assertThat(remaining(again)).isZero();

		jdbc.sql("update studybot.member_days set solved_count = 1 where study_id = :studyId and user_id = :userId")
			.param("studyId", study.studyId())
			.param("userId", alice)
			.update();
		assertThat(use(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.OK);
		assertThat(exemptionUsedAt(study.studyId(), alice, NOV_20)).isEqualTo(NOW);
	}

	@Test
	@DisplayName("수용 기준: 이미 달성한 날에는 면제권을 쓸 수 없다 → 409 GOAL_ALREADY_MET")
	void cannotUseOnAchievedDay() {
		long alice = rows.newUser("s11-achieved");
		TestStudy study = rows.standardStudy(alice);
		rows.putMemberDay(study.studyId(), alice, NOV_20, 1, null, null);

		MvcTestResult result = use(alice, study.studyId(), NOV_20);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(result)).isEqualTo("GOAL_ALREADY_MET");
		assertThat(exemptionUsed(study.studyId(), alice, NOV_20)).isFalse();
	}

	@Test
	@DisplayName("수용 기준: 쉬는 날에는 면제권을 쓸 수 없다 → 400")
	void cannotUseOnRestDay() {
		long alice = rows.newUser("s11-rest");
		TestStudy study = rows.standardStudy(alice, StudyDayRows.SEOUL, LocalTime.MIDNIGHT, 30, "FRIDAY");

		assertThat(use(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(exemptionUsed(study.studyId(), alice, NOV_20)).isFalse();
	}

	@Test
	@DisplayName("수용 기준: 기준 시각이 지난 날(유예 중인 전날 포함)에는 면제권을 쓸 수 없다 → 400")
	void cannotUseAfterBoundary() {
		long alice = rows.newUser("s11-after-boundary");
		TestStudy study = rows.standardStudy(alice);
		clock.setInstant(seoul(NOV_21, 0, 10));

		assertThat(use(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(exemptionUsed(study.studyId(), alice, NOV_20)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-11-21", "2026-11-27", "2026-11-19" })
	@DisplayName("OD-09: 오늘이 아닌 날(미래 날, 지난 날)에는 면제권을 쓸 수 없다 → 400이고 멤버 일 행도 만들지 않는다")
	void od09_cannotUseOnOtherDays(String day) {
		long alice = rows.newUser("s11-od09");
		TestStudy study = rows.standardStudy(alice);
		LocalDate studyDay = LocalDate.parse(day);

		assertThat(use(alice, study.studyId(), studyDay)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(rows.memberDay(study.studyId(), alice, studyDay)).isEmpty();
	}

	@Test
	@DisplayName("판정하지 않는 날(오늘 가입, 기수 밖)에는 면제권을 쓸 수 없다 → 400")
	void cannotUseOnNotJudgedDay() {
		long alice = rows.newUser("s11-not-judged");
		long studyId = rows.standardStudy(rows.newUser("s11-not-judged-other")).studyId();
		rows.join(studyId, alice, seoul(NOV_20, 9, 0), NOV_21);
		long bob = rows.newUser("s11-outside-season");
		TestStudy study = rows.standardStudy(bob);

		assertThat(use(alice, studyId, NOV_20)).hasStatus(HttpStatus.BAD_REQUEST);
		clock.setInstant(seoul(LocalDate.of(2026, 11, 29), 10, 0));
		assertThat(use(bob, study.studyId(), LocalDate.of(2026, 11, 29))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("만들 것 5: 이번 기수 한도를 다 쓰면 409 EXEMPTION_LIMIT_REACHED. 지난 기수에 쓴 것과 성공해 돌려받은 것은 세지 않는다")
	void limitCountsOnlyThisSeasonAndNotReturned() {
		long alice = rows.newUser("s11-limit");
		TestStudy study = rows.standardStudy(alice);
		rows.setExemptionsPerSeasonOfStudy(study.studyId(), 2);
		rows.putMemberDay(study.studyId(), alice, OCT_31, 0, "EXEMPT_TICKET", seoul(OCT_31, 9, 0));
		rows.putMemberDay(study.studyId(), alice, NOV_3, 0, "EXEMPT_TICKET", seoul(NOV_3, 9, 0));
		rows.putMemberDay(study.studyId(), alice, NOV_4, 1, "SUCCESS", seoul(NOV_4, 9, 0));

		MvcTestResult last = use(alice, study.studyId(), NOV_20);
		assertThat(last).hasStatus(HttpStatus.OK);
		assertThat(remaining(last)).isZero();

		clock.setInstant(seoul(NOV_21, 10, 0));
		MvcTestResult over = use(alice, study.studyId(), NOV_21);
		assertThat(over).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(over)).isEqualTo("EXEMPTION_LIMIT_REACHED");
		assertThat(exemptionUsed(study.studyId(), alice, NOV_21)).isFalse();
	}

	@Test
	@DisplayName("남은 개수: 아직 판정 전인 날에 쓴 면제권은 세고, 판정에서 면제(면제권)가 된 날도 센다")
	void remainingCountsUnjudgedAndExemptDays() {
		long alice = rows.newUser("s11-remaining");
		TestStudy study = rows.standardStudy(alice);
		rows.putMemberDay(study.studyId(), alice, NOV_5, 0, "EXEMPT_TICKET", seoul(NOV_5, 9, 0));
		rows.putMemberDay(study.studyId(), alice, NOV_19, 0, null, seoul(NOV_19, 9, 0));

		MvcTestResult result = use(alice, study.studyId(), NOV_20);

		assertThat(remaining(result)).isEqualTo(1);
		assertThat(todayMember(alice, study.studyId(), alice).get("exemptionsRemaining").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("현황: 남은 면제권은 멤버마다 보인다. 기수 밖인 날에는 없다 (null)")
	void statusShowsRemainingPerMember() {
		long alice = rows.newUser("s11-status-remaining");
		long bob = rows.newUser("s11-status-remaining-bob");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), bob, NOV_1);
		assertThat(use(bob, study.studyId(), NOV_20)).hasStatus(HttpStatus.OK);

		assertThat(todayMember(alice, study.studyId(), alice).get("exemptionsRemaining").asInt()).isEqualTo(4);
		assertThat(todayMember(alice, study.studyId(), bob).get("exemptionsRemaining").asInt()).isEqualTo(3);
		clock.setInstant(seoul(LocalDate.of(2026, 11, 29), 10, 0));
		assertThat(todayMember(alice, study.studyId(), alice).get("exemptionsRemaining").isNull()).isTrue();
	}

	@Test
	@DisplayName("T12: 마지막 남은 면제권을 서로 다른 날짜에 동시에 쓰면 한 건만 성공한다. 당일만(OD-09)이라 이 경합은 기준 시각 경계에서 "
			+ "생긴다: 경계 직전 그날(11/20) 요청이 커밋하기 전에 경계 직후 다음 날(11/21) 요청이 들어온다. 멤버십 행 잠금이 둘을 한 줄로 세운다")
	void t12_lastExemptionOnDifferentDaysConcurrently() throws Exception {
		long alice = rows.newUser("s11-t12");
		TestStudy study = rows.standardStudy(alice);
		rows.setExemptionsPerSeasonOfStudy(study.studyId(), 1);
		clock.setInstant(seoul(NOV_20, 23, 59));
		CountDownLatch firstApplied = new CountDownLatch(1);
		CountDownLatch commitFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> usingOnNov20 = executor.submit(() -> new TransactionTemplate(transactionManager)
				.executeWithoutResult(status -> {
					exemptionService.use(study.studyId(), alice, NOV_20);
					firstApplied.countDown();
					awaitQuietly(commitFirst);
				}));
			assertThat(firstApplied.await(10, TimeUnit.SECONDS)).as("11/20 면제권이 커밋 전까지 반영됨").isTrue();

			clock.setInstant(seoul(NOV_21, 0, 1));
			Future<MvcTestResult> usingOnNov21 = executor.submit(() -> use(alice, study.studyId(), NOV_21));
			await().atMost(Duration.ofSeconds(10)).until(() -> someoneWaitsForLockOn("memberships"));
			commitFirst.countDown();

			usingOnNov20.get(10, TimeUnit.SECONDS);
			MvcTestResult second = usingOnNov21.get(10, TimeUnit.SECONDS);
			assertThat(second).hasStatus(HttpStatus.CONFLICT);
			assertThat(code(second)).isEqualTo("EXEMPTION_LIMIT_REACHED");
		}
		finally {
			commitFirst.countDown();
			executor.shutdownNow();
		}
		assertThat(exemptionUsed(study.studyId(), alice, NOV_20)).isTrue();
		assertThat(exemptionUsed(study.studyId(), alice, NOV_21)).isFalse();
	}

	@Test
	@DisplayName("T13 (상태 부분): 면제권을 쓴 날 나중에 풀어도 면제권 기록은 남는다. 현황은 완료이고 면제권 사용 표시가 남는다 (판정은 S12)")
	void t13_solveAfterExemptionKeepsRecord() {
		long alice = rows.newUser("s11-t13");
		TestStudy study = rows.standardStudy(alice);
		assertThat(use(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.OK);
		clock.setInstant(seoul(NOV_20, 23, 0));

		MvcTestResult reported = api.postJson(alice, "/api/studies/{id}/self-reports",
				"{\"studyDay\": \"2026-11-20\", \"platform\": \"BOJ\", \"problemNo\": \"1000\"}", study.studyId());

		assertThat(reported).hasStatus(HttpStatus.CREATED);
		StudyDayRows.MemberDayRow row = rows.memberDay(study.studyId(), alice, NOV_20).orElseThrow();
		assertThat(row.exemptionUsedAt()).isEqualTo(NOW);
		assertThat(row.solvedCount()).isEqualTo(1);
		assertThat(row.result()).isNull();
		JsonNode member = todayMember(alice, study.studyId(), alice);
		assertThat(member.get("status").asString()).isEqualTo("DONE");
		assertThat(member.get("exemptionUsed").asBoolean()).isTrue();
	}

	// --- 취소 ---

	@Test
	@DisplayName("만들 것 5: 기준 시각 전 취소 → 200, 쓴 시각이 지워지고 남은 개수가 돌아온다")
	void cancelTodayClearsAndReturnsTicket() {
		long alice = rows.newUser("s11-cancel");
		TestStudy study = rows.standardStudy(alice);
		assertThat(use(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.OK);

		MvcTestResult result = cancel(alice, study.studyId(), NOV_20);

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = TestApi.json(result);
		assertThat(body.get("exemptionUsed").asBoolean()).isFalse();
		assertThat(body.get("exemptionsRemaining").asInt()).isEqualTo(4);
		assertThat(exemptionUsed(study.studyId(), alice, NOV_20)).isFalse();
		assertThat(todayMember(alice, study.studyId(), alice).get("status").asString()).isEqualTo("REMAINING");
	}

	@Test
	@DisplayName("만들 것 5: 쓰지 않은 날을 취소해도 200이고 바뀌는 것이 없다")
	void cancelWithoutExemptionIsNoop() {
		long alice = rows.newUser("s11-cancel-noop");
		TestStudy study = rows.standardStudy(alice);

		MvcTestResult result = cancel(alice, study.studyId(), NOV_20);

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(remaining(result)).isEqualTo(4);
	}

	@Test
	@DisplayName("R8: 기준 시각이 지난 날의 면제권은 취소할 수 없다 → 400이고 그대로다")
	void cannotCancelAfterBoundary() {
		long alice = rows.newUser("s11-cancel-late");
		TestStudy study = rows.standardStudy(alice);
		assertThat(use(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.OK);
		clock.setInstant(seoul(NOV_21, 0, 1));

		assertThat(cancel(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(exemptionUsed(study.studyId(), alice, NOV_20)).isTrue();
	}

	// --- T19 ---

	@Test
	@DisplayName("T19: 멤버가 아닌 스터디의 면제권 사용·취소 → 404이고 아무것도 남지 않는다")
	void t19_nonMemberCannotUseOrCancel() {
		long alice = rows.newUser("s11-t19");
		long mallory = rows.newUser("s11-t19-mallory");
		TestStudy study = rows.standardStudy(alice);

		assertThat(use(mallory, study.studyId(), NOV_20)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(cancel(mallory, study.studyId(), NOV_20)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(rows.memberDay(study.studyId(), mallory, NOV_20)).isEmpty();
	}

	@Test
	@DisplayName("T19: 탈퇴한 스터디의 면제권 사용 → 404")
	void t19_formerMemberCannotUse() {
		long alice = rows.newUser("s11-t19-left");
		TestStudy study = rows.standardStudy(alice);
		rows.leave(study.membershipId(), seoul(NOV_20, 9, 0));

		assertThat(use(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("T19: 취소는 내 면제권만 지운다. 같은 날 다른 멤버의 면제권은 그대로다")
	void t19_cancelTouchesOnlyMine() {
		long alice = rows.newUser("s11-t19-mine");
		long bob = rows.newUser("s11-t19-bob");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), bob, NOV_1);
		assertThat(use(bob, study.studyId(), NOV_20)).hasStatus(HttpStatus.OK);

		assertThat(cancel(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.OK);

		assertThat(exemptionUsed(study.studyId(), bob, NOV_20)).isTrue();
	}

	// --- 도우미 ---

	/** 다른 연결이 그 테이블의 행 잠금을 기다리고 있는지 (pg_stat_activity). */
	private boolean someoneWaitsForLockOn(String table) {
		return jdbc.sql("""
				select count(*) from pg_stat_activity
				where datname = current_database() and pid <> pg_backend_pid()
					and wait_event_type = 'Lock' and query ilike :pattern
				""").param("pattern", "%" + table + "%").query(Long.class).single() > 0;
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
