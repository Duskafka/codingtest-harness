package com.duskafka.studybot.verification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.StudyDaySolveRow;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import com.duskafka.studybot.verification.service.NewSelfReport;
import com.duskafka.studybot.verification.service.SelfReportService;
import java.sql.Timestamp;
import java.time.Duration;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 수동 인증 제출·철회와 현황의 '수동' 표시·수동 인증 비율 (S11 만들 것 1~4, 수용 기준 T29·철회·링크·T19).
 * {@code POST /api/studies/{id}/self-reports}, {@code DELETE /api/studies/{id}/self-reports/{rid}}.
 *
 * <p>사람이 정한 것 (2026-11-26 S11 세션): 수동 인증은 요청한 스터디의 고른 날에만 반영한다("그 스터디의 고른 날만"). 규칙이 '승인
 * 필요'면 409로 거절한다("409로 거절"). 방장에게 보이는 수동 인증 비율은 이번 기수에서 센다("이번 기수").
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 1기 11-01~11-28 ({@link StudyDayRows#standardStudy}). 지금은 11-20
 * 10:00이다.
 */
@IntegrationTest
class SelfReportIntegrationTest {

	private static final LocalDate NOV_19 = LocalDate.of(2026, 11, 19);

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private static final LocalDate NOV_29 = LocalDate.of(2026, 11, 29);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private SelfReportService selfReportService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private StudyDayRows rows;

	private TestApi api;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		api = new TestApi(mvc, jdbc);
		clock.setInstant(seoul(NOV_20, 10, 0));
	}

	private static String report(LocalDate studyDay, String problemNo) {
		return """
				{"studyDay": "%s", "platform": "BOJ", "problemNo": "%s"}
				""".formatted(studyDay, problemNo);
	}

	private MvcTestResult submit(long userId, long studyId, String body) {
		return api.postJson(userId, "/api/studies/{id}/self-reports", body, studyId);
	}

	private MvcTestResult withdraw(long userId, long studyId, long reportId) {
		return api.delete(userId, "/api/studies/{id}/self-reports/{rid}", studyId, reportId);
	}

	private static String code(MvcTestResult result) {
		JsonNode code = TestApi.json(result).get("code");
		return code == null ? "(없음)" : code.asString();
	}

	private static long id(MvcTestResult result) {
		return TestApi.json(result).get("id").asLong();
	}

	private int selfReportCount(long studyId, long userId) {
		return jdbc.sql("select count(*) from studybot.self_reports where study_id = :studyId and user_id = :userId")
			.param("studyId", studyId)
			.param("userId", userId)
			.query(Integer.class)
			.single();
	}

	private String selfReportStatus(long reportId) {
		return jdbc.sql("select status from studybot.self_reports where id = :id")
			.param("id", reportId)
			.query(String.class)
			.single();
	}

	private long solveRecordOf(long reportId) {
		return jdbc.sql("select solve_record_id from studybot.self_reports where id = :id")
			.param("id", reportId)
			.query(Long.class)
			.single();
	}

	private int solveRecordCount(long userId) {
		return jdbc.sql("select count(*) from studybot.solve_records where user_id = :userId")
			.param("userId", userId)
			.query(Integer.class)
			.single();
	}

	private JsonNode today(long userId, long studyId) {
		MvcTestResult result = api.get(userId, "/api/studies/{id}/today", studyId);
		assertThat(result).hasStatus(HttpStatus.OK);
		return TestApi.json(result);
	}

	private static JsonNode memberOf(JsonNode status, long userId) {
		for (JsonNode member : status.get("members")) {
			if (member.get("userId").asLong() == userId) {
				return member;
			}
		}
		throw new AssertionError("멤버가 없다: " + userId);
	}

	// --- 제출 ---

	@Test
	@DisplayName("만들 것 1·2: 오늘 수동 인증 → 201, 풀이 기록(SELF_REPORT, 인정 시각 = 제출 시각)을 만들고 그 스터디의 오늘에 바로 센다")
	void submitTodayCountsImmediately() {
		long alice = rows.newUser("s11-submit");
		TestStudy study = rows.standardStudy(alice);

		MvcTestResult result = submit(alice, study.studyId(), """
				{"studyDay": "2026-11-20", "platform": "BOJ", "problemNo": "1000",
				 "evidenceUrl": "https://www.acmicpc.net/source/1", "memo": "백준 서버 문제로 수동 인증"}
				""");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		JsonNode body = TestApi.json(result);
		assertThat(body.get("studyId").asLong()).isEqualTo(study.studyId());
		assertThat(body.get("studyDay").asString()).isEqualTo("2026-11-20");
		assertThat(body.get("platform").asString()).isEqualTo("BOJ");
		assertThat(body.get("problemNo").asString()).isEqualTo("1000");
		assertThat(body.get("evidenceUrl").asString()).isEqualTo("https://www.acmicpc.net/source/1");
		assertThat(body.get("memo").asString()).isEqualTo("백준 서버 문제로 수동 인증");
		assertThat(body.get("status").asString()).isEqualTo("ACCEPTED");
		long solveRecordId = solveRecordOf(id(result));
		assertThat(jdbc.sql("select source, status, received_at from studybot.solve_records where id = :id")
			.param("id", solveRecordId)
			.query((rs, n) -> rs.getString("source") + " " + rs.getString("status") + " "
					+ rs.getTimestamp("received_at").toInstant())
			.single()).isEqualTo("SELF_REPORT CONFIRMED " + seoul(NOV_20, 10, 0));
		assertThat(rows.studyDaySolves(study.studyId(), alice))
			.containsExactly(new StudyDaySolveRow(NOV_20, solveRecordId, "BOJ", "1000"));
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isEqualTo(1);
	}

	@Test
	@DisplayName("수용 기준: 수동 인증은 제출하자마자 현황에 반영되고 '수동'(SELF_REPORT)으로 표시된다")
	void submittedReportShowsAsSelfReportInStatus() {
		long alice = rows.newUser("s11-status");
		TestStudy study = rows.standardStudy(alice);

		assertThat(submit(alice, study.studyId(), report(NOV_20, "1000"))).hasStatus(HttpStatus.CREATED);

		JsonNode status = today(alice, study.studyId());
		assertThat(memberOf(status, alice).get("status").asString()).isEqualTo("DONE");
		assertThat(memberOf(status, alice).get("solvedCount").asInt()).isEqualTo(1);
		assertThat(status.get("recentSolves")).hasSize(1);
		assertThat(status.get("recentSolves").get(0).get("source").asString()).isEqualTo("SELF_REPORT");
		assertThat(status.get("recentSolves").get(0).get("problemNo").asString()).isEqualTo("1000");
	}

	@Test
	@DisplayName("T29·R4: 유예 중인 전날이 R4의 전날 후보(목표 미달)면 수동 인증할 수 있고 전날에 센다. 전날이 목표를 채운 뒤에는 "
			+ "409 PREVIOUS_DAY_NOT_OPEN (사람이 정함 \"R4 전날 조건 적용\")")
	void t29_previousDayDuringGraceIsAllowed() {
		long alice = rows.newUser("s11-grace");
		TestStudy study = rows.standardStudy(alice);
		clock.setInstant(seoul(NOV_21, 0, 20));

		assertThat(submit(alice, study.studyId(), report(NOV_20, "1000"))).hasStatus(HttpStatus.CREATED);
		MvcTestResult second = submit(alice, study.studyId(), report(NOV_20, "1001"));

		assertThat(second).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(second)).isEqualTo("PREVIOUS_DAY_NOT_OPEN");
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isEqualTo(1);
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_21)).isZero();
		assertThat(selfReportCount(study.studyId(), alice)).isEqualTo(1);
	}

	@Test
	@DisplayName("R4·R8: 전날 면제권을 썼으면 유예 중에 전날로 수동 인증할 수 없다 → 409 PREVIOUS_DAY_NOT_OPEN, 면제권은 그대로 "
			+ "(자정 뒤 수동 인증으로 '성공 + 면제권 반환'을 만들지 못하게, 사람이 정함 \"R4 전날 조건 적용\")")
	void previousDayWithExemptionIsRejected() {
		long alice = rows.newUser("s11-grace-ticket");
		TestStudy study = rows.standardStudy(alice);
		rows.putMemberDay(study.studyId(), alice, NOV_20, 0, null, seoul(NOV_20, 9, 0));
		clock.setInstant(seoul(NOV_21, 0, 20));

		MvcTestResult result = submit(alice, study.studyId(), report(NOV_20, "1000"));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(result)).isEqualTo("PREVIOUS_DAY_NOT_OPEN");
		assertThat(selfReportCount(study.studyId(), alice)).isZero();
		assertThat(solveRecordCount(alice)).isZero();
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isZero();
		assertThat(rows.memberDay(study.studyId(), alice, NOV_20).orElseThrow().exemptionUsedAt()).isNotNull();
	}

	@Test
	@DisplayName("R4: 전날이 쉬는 날이면 유예 중에 전날로 수동 인증할 수 없다 → 409 PREVIOUS_DAY_NOT_OPEN")
	void previousRestDayIsRejected() {
		long alice = rows.newUser("s11-grace-rest");
		TestStudy study = rows.standardStudy(alice, StudyDayRows.SEOUL, LocalTime.MIDNIGHT, 30, "FRIDAY");
		clock.setInstant(seoul(NOV_21, 0, 20));

		MvcTestResult result = submit(alice, study.studyId(), report(NOV_20, "1000"));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(result)).isEqualTo("PREVIOUS_DAY_NOT_OPEN");
		assertThat(selfReportCount(study.studyId(), alice)).isZero();
	}

	@Test
	@DisplayName("R4: 전날이 판정하지 않는 날(전날 가입)이면 유예 중에 전날로 수동 인증할 수 없다 → 409 PREVIOUS_DAY_NOT_OPEN. 오늘로는 낼 수 있다")
	void previousNotJudgedDayIsRejected() {
		long alice = rows.newUser("s11-grace-joined");
		long studyId = rows.standardStudy(rows.newUser("s11-grace-joined-other")).studyId();
		rows.join(studyId, alice, seoul(NOV_20, 9, 0), NOV_21);
		clock.setInstant(seoul(NOV_21, 0, 20));

		MvcTestResult result = submit(alice, studyId, report(NOV_20, "1000"));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(result)).isEqualTo("PREVIOUS_DAY_NOT_OPEN");
		assertThat(submit(alice, studyId, report(NOV_21, "1000"))).hasStatus(HttpStatus.CREATED);
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-11-19", "2026-11-21", "2026-11-18" })
	@DisplayName("T29: 기한 밖 날짜(오늘도 유예 중 전날도 아님) → 400이고 아무것도 남지 않는다")
	void t29_dayOutsideWindowIsRejected(String day) {
		long alice = rows.newUser("s11-window");
		TestStudy study = rows.standardStudy(alice);

		MvcTestResult result = submit(alice, study.studyId(), report(LocalDate.parse(day), "1000"));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(selfReportCount(study.studyId(), alice)).isZero();
		assertThat(solveRecordCount(alice)).isZero();
		assertThat(rows.studyDaySolves(study.studyId(), alice)).isEmpty();
	}

	@Test
	@DisplayName("T29: 유예가 끝난 뒤의 전날 → 400")
	void t29_previousDayAfterGraceIsRejected() {
		long alice = rows.newUser("s11-after-grace");
		TestStudy study = rows.standardStudy(alice);
		clock.setInstant(seoul(NOV_21, 0, 30));

		assertThat(submit(alice, study.studyId(), report(NOV_20, "1000"))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(selfReportCount(study.studyId(), alice)).isZero();
	}

	@Test
	@DisplayName("T29: 하루 6건째 수동 인증 → 409 SELF_REPORT_DAILY_LIMIT이고 6건째는 아무것도 남지 않는다")
	void t29_sixthReportOfTheDayIsRejected() {
		long alice = rows.newUser("s11-limit");
		TestStudy study = rows.standardStudy(alice);
		for (int i = 1; i <= 5; i++) {
			assertThat(submit(alice, study.studyId(), report(NOV_20, "100" + i))).hasStatus(HttpStatus.CREATED);
		}

		MvcTestResult sixth = submit(alice, study.studyId(), report(NOV_20, "1006"));

		assertThat(sixth).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(sixth)).isEqualTo("SELF_REPORT_DAILY_LIMIT");
		assertThat(selfReportCount(study.studyId(), alice)).isEqualTo(5);
		assertThat(solveRecordCount(alice)).isEqualTo(5);
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isEqualTo(5);
	}

	@Test
	@DisplayName("T29: 하루 5건은 스터디 일마다 센다 (유예 중 전날 5건과 오늘 5건은 따로). 철회한 건도 받은 건으로 센다")
	void t29_limitIsPerStudyDayAndCountsWithdrawn() {
		long alice = rows.newUser("s11-limit-day");
		TestStudy study = rows.standardStudy(alice);
		jdbc.sql("update studybot.study_rules set daily_goal = 5 where study_id = :studyId")
			.param("studyId", study.studyId())
			.update();
		clock.setInstant(seoul(NOV_21, 0, 10));
		for (int i = 1; i <= 5; i++) {
			assertThat(submit(alice, study.studyId(), report(NOV_20, "100" + i))).hasStatus(HttpStatus.CREATED);
		}
		long withdrawn = -1;
		for (int i = 1; i <= 5; i++) {
			MvcTestResult result = submit(alice, study.studyId(), report(NOV_21, "200" + i));
			assertThat(result).hasStatus(HttpStatus.CREATED);
			withdrawn = id(result);
		}
		assertThat(withdraw(alice, study.studyId(), withdrawn)).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult again = submit(alice, study.studyId(), report(NOV_21, "3001"));

		assertThat(again).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(again)).isEqualTo("SELF_REPORT_DAILY_LIMIT");
	}

	@Test
	@DisplayName("T29: 5건째와 6건째가 동시에 오면 6건째는 409다 (멤버 일 행 잠금으로 세는 일을 한 줄로 세움)")
	void t29_concurrentSixthReportIsRejected() throws Exception {
		long alice = rows.newUser("s11-limit-race");
		TestStudy study = rows.standardStudy(alice);
		for (int i = 1; i <= 4; i++) {
			assertThat(submit(alice, study.studyId(), report(NOV_20, "100" + i))).hasStatus(HttpStatus.CREATED);
		}
		CountDownLatch fifthApplied = new CountDownLatch(1);
		CountDownLatch commitFifth = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> submittingFifth = executor.submit(() -> new TransactionTemplate(transactionManager)
				.executeWithoutResult(status -> {
					selfReportService.submit(study.studyId(), alice,
							new NewSelfReport(NOV_20, new ProblemRef(Platform.BOJ, "1005"), null, null));
					fifthApplied.countDown();
					awaitQuietly(commitFifth);
				}));
			assertThat(fifthApplied.await(10, TimeUnit.SECONDS)).as("5건째가 커밋 전까지 반영됨").isTrue();

			Future<MvcTestResult> submittingSixth = executor
				.submit(() -> submit(alice, study.studyId(), report(NOV_20, "1006")));
			await().atMost(Duration.ofSeconds(10)).until(() -> someoneWaitsForLockOn("member_days"));
			commitFifth.countDown();

			submittingFifth.get(10, TimeUnit.SECONDS);
			MvcTestResult sixth = submittingSixth.get(10, TimeUnit.SECONDS);
			assertThat(sixth).hasStatus(HttpStatus.CONFLICT);
			assertThat(code(sixth)).isEqualTo("SELF_REPORT_DAILY_LIMIT");
		}
		finally {
			commitFifth.countDown();
			executor.shutdownNow();
		}
		assertThat(selfReportCount(study.studyId(), alice)).isEqualTo(5);
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isEqualTo(5);
	}

	@ParameterizedTest
	@ValueSource(strings = { "javascript:alert(1)", "JAVASCRIPT:alert(1)", "ftp://example.com/a", "data:text/html,x",
			"//example.com/a", "https://", "https://exa mple.com" })
	@DisplayName("9.7: http·https가 아닌 증거 링크 → 400이고 아무것도 남지 않는다")
	void nonHttpEvidenceLinkIsRejected(String link) {
		long alice = rows.newUser("s11-link");
		TestStudy study = rows.standardStudy(alice);

		MvcTestResult result = submit(alice, study.studyId(), """
				{"studyDay": "2026-11-20", "platform": "BOJ", "problemNo": "1000", "evidenceUrl": "%s"}
				""".formatted(link));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(selfReportCount(study.studyId(), alice)).isZero();
		assertThat(solveRecordCount(alice)).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://example.com/a", "HTTPS://EXAMPLE.COM/A?b=1#c" })
	@DisplayName("9.7: http·https 증거 링크는 받는다")
	void httpEvidenceLinkIsAccepted(String link) {
		long alice = rows.newUser("s11-link-ok");
		TestStudy study = rows.standardStudy(alice);

		MvcTestResult result = submit(alice, study.studyId(), """
				{"studyDay": "2026-11-20", "platform": "BOJ", "problemNo": "1000", "evidenceUrl": "%s"}
				""".formatted(link));

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(TestApi.json(result).get("evidenceUrl").asString()).isEqualTo(link);
	}

	@Test
	@DisplayName("9.7: DB도 http·https가 아닌 증거 링크를 막는다 (CHECK)")
	void schemaRejectsNonHttpEvidenceLink() {
		long alice = rows.newUser("s11-link-db");
		TestStudy study = rows.standardStudy(alice);
		long solveId = rows.solve(alice, "BOJ", "1000", seoul(NOV_20, 10, 0));

		assertThatThrownBy(() -> jdbc.sql("""
				insert into studybot.self_reports (study_id, user_id, study_day, platform, problem_no, evidence_url, status,
					solve_record_id, created_at)
				values (:studyId, :userId, :day, 'BOJ', '1000', 'javascript:alert(1)', 'ACCEPTED', :solveId, :createdAt)
				""")
			.param("studyId", study.studyId())
			.param("userId", alice)
			.param("day", NOV_20)
			.param("solveId", solveId)
			.param("createdAt", Timestamp.from(seoul(NOV_20, 10, 0)))
			.update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"platform\": \"BOJ\", \"problemNo\": \"1000\"}",
			"{\"studyDay\": \"2026-11-20\", \"problemNo\": \"1000\"}",
			"{\"studyDay\": \"2026-11-20\", \"platform\": \"BOJ\", \"problemNo\": \"01000\"}",
			"{\"studyDay\": \"2026-11-20\", \"platform\": \"CODEFORCES\", \"problemNo\": \"1000\"}" })
	@DisplayName("날짜·문제가 없거나 형식이 틀리면 400")
	void invalidRequestIsRejected(String body) {
		long alice = rows.newUser("s11-invalid");
		TestStudy study = rows.standardStudy(alice);

		assertThat(submit(alice, study.studyId(), body)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(solveRecordCount(alice)).isZero();
	}

	@Test
	@DisplayName("메모는 500자까지 받는다")
	void memoLongerThanLimitIsRejected() {
		long alice = rows.newUser("s11-memo");
		TestStudy study = rows.standardStudy(alice);
		String body = """
				{"studyDay": "2026-11-20", "platform": "BOJ", "problemNo": "1000", "memo": "%s"}
				""";

		assertThat(submit(alice, study.studyId(), body.formatted("가".repeat(501)))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(submit(alice, study.studyId(), body.formatted("가".repeat(500)))).hasStatus(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("알려진 충돌 6번: 규칙이 '승인 필요'면 409 SELF_REPORT_APPROVAL_UNSUPPORTED이고 아무것도 남지 않는다 (사람이 정함 \"409로 거절\")")
	void approvalRequiredModeIsRejected() {
		long alice = rows.newUser("s11-approval");
		TestStudy study = rows.standardStudy(alice);
		rows.setSelfReportMode(study.studyId(), "APPROVAL_REQUIRED");

		MvcTestResult result = submit(alice, study.studyId(), report(NOV_20, "1000"));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(result)).isEqualTo("SELF_REPORT_APPROVAL_UNSUPPORTED");
		assertThat(selfReportCount(study.studyId(), alice)).isZero();
		assertThat(solveRecordCount(alice)).isZero();
	}

	@Test
	@DisplayName("5.6: 이미 판정된 날에는 수동 인증할 수 없다 → 409 STUDY_DAY_JUDGED")
	void judgedDayIsRejected() {
		long alice = rows.newUser("s11-judged");
		TestStudy study = rows.standardStudy(alice);
		clock.setInstant(seoul(NOV_21, 0, 10));
		rows.markJudged(study.studyId(), alice, NOV_20, "FAIL");

		MvcTestResult result = submit(alice, study.studyId(), report(NOV_20, "1000"));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(result)).isEqualTo("STUDY_DAY_JUDGED");
		assertThat(selfReportCount(study.studyId(), alice)).isZero();
		assertThat(rows.studyDaySolves(study.studyId(), alice)).isEmpty();
	}

	@Test
	@DisplayName("R5: 그날 이미 센 문제 → 409 PROBLEM_ALREADY_COUNTED이고 아무것도 남지 않는다 (하루 5건에도 세지 않음)")
	void problemAlreadyCountedThatDayIsRejected() {
		long alice = rows.newUser("s11-dup");
		TestStudy study = rows.standardStudy(alice);
		long github = rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_20, 9, 0));
		rows.putCounted(study.studyId(), alice, NOV_20, github, "BOJ", "1000");

		MvcTestResult result = submit(alice, study.studyId(), report(NOV_20, "1000"));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(result)).isEqualTo("PROBLEM_ALREADY_COUNTED");
		assertThat(selfReportCount(study.studyId(), alice)).isZero();
		assertThat(solveRecordCount(alice)).isEqualTo(1);
		assertThat(rows.studyDaySolves(study.studyId(), alice)).extracting(StudyDaySolveRow::solveRecordId)
			.containsExactly(github);
	}

	@Test
	@DisplayName("사람이 정함 \"그 스터디의 고른 날만\": 다른 스터디에는 세지 않는다")
	void reportCountsOnlyInThatStudy() {
		long alice = rows.newUser("s11-scope");
		TestStudy first = rows.standardStudy(alice);
		TestStudy second = rows.standardStudy(alice);

		assertThat(submit(alice, first.studyId(), report(NOV_20, "1000"))).hasStatus(HttpStatus.CREATED);

		assertThat(rows.solvedCount(first.studyId(), alice, NOV_20)).isEqualTo(1);
		assertThat(rows.studyDaySolves(second.studyId(), alice)).isEmpty();
		assertThat(rows.memberDay(second.studyId(), alice, NOV_20)).isEmpty();
	}

	@Test
	@DisplayName("R6와 같이: 판정하지 않는 날(가입한 날)에도 기록하고 센다. 판정에는 영향이 없다")
	void reportOnNotJudgedDayIsRecorded() {
		long alice = rows.newUser("s11-joined-today");
		long studyId = rows.standardStudy(rows.newUser("s11-other")).studyId();
		rows.join(studyId, alice, seoul(NOV_20, 9, 0), NOV_21);

		assertThat(submit(alice, studyId, report(NOV_20, "1000"))).hasStatus(HttpStatus.CREATED);

		assertThat(rows.solvedCount(studyId, alice, NOV_20)).isEqualTo(1);
		assertThat(memberOf(today(alice, studyId), alice).get("status").asString()).isEqualTo("NOT_JUDGED");
	}

	// --- 철회 ---

	@Test
	@DisplayName("만들 것 3: 판정 전 철회 → 204, 집계에서 빠지고 현황에도 없다. 수동 인증은 WITHDRAWN, 풀이 기록은 IGNORED")
	void withdrawBeforeJudgmentRemovesFromCount() {
		long alice = rows.newUser("s11-withdraw");
		TestStudy study = rows.standardStudy(alice);
		long kept = id(submit(alice, study.studyId(), report(NOV_20, "1000")));
		long withdrawn = id(submit(alice, study.studyId(), report(NOV_20, "1001")));

		assertThat(withdraw(alice, study.studyId(), withdrawn)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(selfReportStatus(withdrawn)).isEqualTo("WITHDRAWN");
		assertThat(selfReportStatus(kept)).isEqualTo("ACCEPTED");
		assertThat(rows.solveRecord(solveRecordOf(withdrawn)).status()).isEqualTo("IGNORED");
		assertThat(rows.studyDaySolves(study.studyId(), alice)).extracting(StudyDaySolveRow::problemNo)
			.containsExactly("1000");
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isEqualTo(1);
		assertThat(today(alice, study.studyId()).get("recentSolves")).hasSize(1);
	}

	@Test
	@DisplayName("만들 것 3: 유예 중 전날에 낸 것도 판정 전이면 철회할 수 있다")
	void withdrawPreviousDayReportBeforeJudgment() {
		long alice = rows.newUser("s11-withdraw-grace");
		TestStudy study = rows.standardStudy(alice);
		clock.setInstant(seoul(NOV_21, 0, 10));
		long reportId = id(submit(alice, study.studyId(), report(NOV_20, "1000")));
		clock.setInstant(seoul(NOV_21, 0, 35));

		assertThat(withdraw(alice, study.studyId(), reportId)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isZero();
	}

	@Test
	@DisplayName("수용 기준: 판정 후 철회 → 409 SELF_REPORT_DAY_JUDGED이고 그대로다 (이의 안내를 띄우는 STUDY_DAY_JUDGED와 다름)")
	void withdrawAfterJudgmentIsRejected() {
		long alice = rows.newUser("s11-withdraw-judged");
		TestStudy study = rows.standardStudy(alice);
		long reportId = id(submit(alice, study.studyId(), report(NOV_20, "1000")));
		rows.markJudged(study.studyId(), alice, NOV_20, "SUCCESS");

		MvcTestResult result = withdraw(alice, study.studyId(), reportId);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(result)).isEqualTo("SELF_REPORT_DAY_JUDGED");
		assertThat(selfReportStatus(reportId)).isEqualTo("ACCEPTED");
		assertThat(rows.solveRecord(solveRecordOf(reportId)).status()).isEqualTo("CONFIRMED");
		assertThat(rows.studyDaySolves(study.studyId(), alice)).hasSize(1);
	}

	@Test
	@DisplayName("이미 철회한 것을 다시 철회 → 409 SELF_REPORT_NOT_ACCEPTED")
	void withdrawTwiceIsConflict() {
		long alice = rows.newUser("s11-withdraw-twice");
		TestStudy study = rows.standardStudy(alice);
		long reportId = id(submit(alice, study.studyId(), report(NOV_20, "1000")));
		assertThat(withdraw(alice, study.studyId(), reportId)).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult again = withdraw(alice, study.studyId(), reportId);

		assertThat(again).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(again)).isEqualTo("SELF_REPORT_NOT_ACCEPTED");
	}

	@Test
	@DisplayName("철회 (사람이 정함 \"다른 풀이로 바꿔 끼움\"): 그날 받은 같은 문제의 push가 R5로 세지 않은 채 남아 있으면, 철회 뒤에는 그 push가 "
			+ "대신 센다. 센 수가 줄지 않고 현황의 최근 풀이는 GITHUB다")
	void withdrawKeepsSameDayPushCounted() {
		long alice = rows.newUser("s11-replace");
		TestStudy study = rows.standardStudy(alice);
		long reportId = id(submit(alice, study.studyId(), report(NOV_20, "1000")));
		long push = rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_20, 15, 0));
		clock.setInstant(seoul(NOV_20, 16, 0));

		assertThat(withdraw(alice, study.studyId(), reportId)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(selfReportStatus(reportId)).isEqualTo("WITHDRAWN");
		assertThat(rows.studyDaySolves(study.studyId(), alice))
			.containsExactly(new StudyDaySolveRow(NOV_20, push, "BOJ", "1000"));
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isEqualTo(1);
		JsonNode recent = today(alice, study.studyId()).get("recentSolves");
		assertThat(recent).hasSize(1);
		assertThat(recent.get(0).get("source").asString()).isEqualTo("GITHUB");
	}

	@Test
	@DisplayName("철회: 대신 셀 push가 둘 이상이면 받은 시각이 빠른 것을 고른다 (ID 순서가 아님)")
	void withdrawPicksEarliestReceivedPush() {
		long alice = rows.newUser("s11-replace-order");
		TestStudy study = rows.standardStudy(alice);
		long reportId = id(submit(alice, study.studyId(), report(NOV_20, "1000")));
		long later = rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_20, 15, 0));
		long earlier = rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_20, 12, 0));
		assertThat(earlier).isGreaterThan(later);
		clock.setInstant(seoul(NOV_20, 16, 0));

		assertThat(withdraw(alice, study.studyId(), reportId)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(rows.studyDaySolves(study.studyId(), alice))
			.containsExactly(new StudyDaySolveRow(NOV_20, earlier, "BOJ", "1000"));
	}

	@Test
	@DisplayName("철회: 풀이로 보지 않는(IGNORED) 같은 문제 기록으로는 바꿔 끼우지 않는다")
	void withdrawDoesNotUseIgnoredRecord() {
		long alice = rows.newUser("s11-replace-ignored");
		TestStudy study = rows.standardStudy(alice);
		long reportId = id(submit(alice, study.studyId(), report(NOV_20, "1000")));
		long ignored = rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_20, 15, 0));
		jdbc.sql("update studybot.solve_records set status = 'IGNORED' where id = :id").param("id", ignored).update();
		clock.setInstant(seoul(NOV_20, 16, 0));

		assertThat(withdraw(alice, study.studyId(), reportId)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(rows.studyDaySolves(study.studyId(), alice)).isEmpty();
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isZero();
	}

	@Test
	@DisplayName("철회: 그 스터디 일 밖(전날 밤, 다음 날 유예)에 받은 같은 문제 push로는 바꿔 끼우지 않는다")
	void withdrawDoesNotUsePushOutsideTheDay() {
		long alice = rows.newUser("s11-replace-outside");
		TestStudy study = rows.standardStudy(alice);
		long reportId = id(submit(alice, study.studyId(), report(NOV_20, "1000")));
		rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_19, 23, 59));
		rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_21, 0, 0));
		clock.setInstant(seoul(NOV_21, 0, 20));

		assertThat(withdraw(alice, study.studyId(), reportId)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(rows.studyDaySolves(study.studyId(), alice)).isEmpty();
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isZero();
	}

	@Test
	@DisplayName("철회: 다른 스터디에 낸 같은 문제의 수동 인증으로는 바꿔 끼우지 않는다 (사람이 정함 \"그 스터디의 고른 날만\"). 그 스터디는 그대로다")
	void withdrawDoesNotUseSelfReportOfAnotherStudy() {
		long alice = rows.newUser("s11-replace-other-study");
		TestStudy first = rows.standardStudy(alice);
		TestStudy second = rows.standardStudy(alice);
		long reportId = id(submit(alice, first.studyId(), report(NOV_20, "1000")));
		assertThat(submit(alice, second.studyId(), report(NOV_20, "1000"))).hasStatus(HttpStatus.CREATED);

		assertThat(withdraw(alice, first.studyId(), reportId)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(rows.studyDaySolves(first.studyId(), alice)).isEmpty();
		assertThat(rows.solvedCount(first.studyId(), alice, NOV_20)).isZero();
		assertThat(rows.solvedCount(second.studyId(), alice, NOV_20)).isEqualTo(1);
	}

	@Test
	@DisplayName("철회: 이 스터디의 다른 날에 이미 센 같은 문제 push로는 바꿔 끼우지 않는다 (풀이 하나를 두 날에 세지 않음)")
	void withdrawDoesNotUsePushCountedOnAnotherDay() {
		long alice = rows.newUser("s11-replace-counted");
		TestStudy study = rows.standardStudy(alice);
		long push = rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_20, 0, 10));
		rows.putCounted(study.studyId(), alice, NOV_19, push, "BOJ", "1000");
		long reportId = id(submit(alice, study.studyId(), report(NOV_20, "1000")));

		assertThat(withdraw(alice, study.studyId(), reportId)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(rows.studyDaySolves(study.studyId(), alice))
			.containsExactly(new StudyDaySolveRow(NOV_19, push, "BOJ", "1000"));
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isZero();
	}

	// --- T19 ---

	@Test
	@DisplayName("T19: 멤버가 아닌 스터디에 수동 인증 → 404이고 아무것도 남지 않는다")
	void t19_nonMemberCannotSubmit() {
		long alice = rows.newUser("s11-t19-member");
		long mallory = rows.newUser("s11-t19-mallory");
		TestStudy study = rows.standardStudy(alice);

		assertThat(submit(mallory, study.studyId(), report(NOV_20, "1000"))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(solveRecordCount(mallory)).isZero();
		assertThat(selfReportCount(study.studyId(), mallory)).isZero();
	}

	@Test
	@DisplayName("T19: 탈퇴한 스터디에 수동 인증 → 404")
	void t19_formerMemberCannotSubmit() {
		long alice = rows.newUser("s11-t19-left");
		TestStudy study = rows.standardStudy(alice);
		rows.leave(study.membershipId(), seoul(NOV_20, 9, 0));

		assertThat(submit(alice, study.studyId(), report(NOV_20, "1000"))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("T19: 남의 수동 인증, 다른 스터디 주소로 내 수동 인증, 없는 수동 인증을 철회 → 404이고 그대로다")
	void t19_cannotWithdrawOthersReport() {
		long alice = rows.newUser("s11-t19-owner");
		long bob = rows.newUser("s11-t19-bob");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), bob, NOV_1);
		TestStudy other = rows.standardStudy(alice);
		long reportId = id(submit(alice, study.studyId(), report(NOV_20, "1000")));

		assertThat(withdraw(bob, study.studyId(), reportId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(withdraw(alice, other.studyId(), reportId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(withdraw(alice, study.studyId(), reportId + 100_000)).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(selfReportStatus(reportId)).isEqualTo("ACCEPTED");
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isEqualTo(1);
	}

	@Test
	@DisplayName("T19: 탈퇴한 뒤에는 그 스터디의 내 수동 인증을 철회할 수 없다 → 404")
	void t19_formerMemberCannotWithdraw() {
		long alice = rows.newUser("s11-t19-left-withdraw");
		TestStudy study = rows.standardStudy(alice);
		long reportId = id(submit(alice, study.studyId(), report(NOV_20, "1000")));
		rows.leave(study.membershipId(), seoul(NOV_20, 11, 0));

		assertThat(withdraw(alice, study.studyId(), reportId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(selfReportStatus(reportId)).isEqualTo("ACCEPTED");
	}

	// --- 수동 인증 비율 (방장에게) ---

	@Test
	@DisplayName("만들 것 4: 방장은 멤버별 수동 인증 비율(이번 기수에 센 풀이 중 수동 인증)을 본다. 지난 기수의 풀이는 세지 않는다")
	void ownerSeesSelfReportRatioOfThisSeason() {
		long owner = rows.newUser("s11-ratio-owner");
		long alice = rows.newUser("s11-ratio-alice");
		long bob = rows.newUser("s11-ratio-bob");
		TestStudy study = rows.standardStudy(alice);
		rows.joinAsOwner(study.studyId(), owner, NOV_1);
		rows.join(study.studyId(), bob, NOV_1);
		long github = rows.githubSolve(alice, "BOJ", "2000", "A-B", seoul(NOV_19, 9, 0));
		rows.putCounted(study.studyId(), alice, NOV_19, github, "BOJ", "2000");
		long beforeSeason = rows.githubSolve(alice, "BOJ", "3000", "old", seoul(LocalDate.of(2026, 10, 30), 9, 0));
		rows.putCounted(study.studyId(), alice, LocalDate.of(2026, 10, 30), beforeSeason, "BOJ", "3000");
		assertThat(submit(alice, study.studyId(), report(NOV_20, "1000"))).hasStatus(HttpStatus.CREATED);

		JsonNode status = today(owner, study.studyId());

		assertThat(memberOf(status, alice).get("selfReportRatio").asDouble()).isEqualTo(0.5);
		assertThat(memberOf(status, bob).get("selfReportRatio").isNull()).as("센 풀이가 없으면 비율이 없다").isTrue();
		assertThat(memberOf(status, owner).get("selfReportRatio").isNull()).isTrue();
	}

	@Test
	@DisplayName("만들 것 4: 방장이 아니면 수동 인증 비율을 받지 않는다 (null)")
	void memberDoesNotSeeSelfReportRatio() {
		long alice = rows.newUser("s11-ratio-member");
		TestStudy study = rows.standardStudy(alice);
		assertThat(submit(alice, study.studyId(), report(NOV_20, "1000"))).hasStatus(HttpStatus.CREATED);

		JsonNode status = today(alice, study.studyId());

		assertThat(memberOf(status, alice).get("selfReportRatio").isNull()).isTrue();
	}

	@Test
	@DisplayName("만들 것 4: 기수 밖인 날의 현황에는 수동 인증 비율이 없다 (null)")
	void noSelfReportRatioOutsideSeason() {
		long owner = rows.newUser("s11-ratio-outside");
		long alice = rows.newUser("s11-ratio-outside-alice");
		TestStudy study = rows.standardStudy(alice);
		rows.joinAsOwner(study.studyId(), owner, NOV_1);
		clock.setInstant(seoul(NOV_29, 10, 0));
		assertThat(submit(alice, study.studyId(), report(NOV_29, "1000"))).hasStatus(HttpStatus.CREATED);

		JsonNode status = today(owner, study.studyId());

		assertThat(memberOf(status, alice).get("selfReportRatio").isNull()).isTrue();
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
