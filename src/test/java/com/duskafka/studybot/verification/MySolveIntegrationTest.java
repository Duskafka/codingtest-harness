package com.duskafka.studybot.verification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.MemberDayRow;
import com.duskafka.studybot.progress.StudyDayRows.SolveRecordRow;
import com.duskafka.studybot.progress.StudyDayRows.StudyDaySolveRow;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import com.duskafka.studybot.verification.service.MySolveService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 내 풀이와 확인 대기 지정 (S10 만들 것 3·4, 수용 기준 T26·409·남의 풀이·페이지 크기 상한·T19). {@code GET /api/me/solves},
 * {@code PATCH /api/me/solves/{id}}.
 *
 * <p>풀이 기록은 {@link StudyDayRows}로 바로 넣는다. push 웹훅으로 만든 확인 대기에 지정하는 경우는
 * {@code github.PendingSolveIdentificationIntegrationTest}가 본다.
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 1기 11-01~11-28 ({@link StudyDayRows#standardStudy}).
 */
@IntegrationTest
class MySolveIntegrationTest {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private static final LocalDate NOV_22 = LocalDate.of(2026, 11, 22);

	private static final String BOJ_1000 = "{\"platform\": \"BOJ\", \"problemNo\": \"1000\"}";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private MySolveService mySolveService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private StudyDayRows rows;

	private TestApi api;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		api = new TestApi(mvc, jdbc);
		clock.setInstant(seoul(NOV_22, 10, 0));
	}

	private MvcTestResult identify(long userId, long solveId, String body) {
		return api.patchJson(userId, "/api/me/solves/{id}", body, solveId);
	}

	private MvcTestResult mySolves(long userId, String query) {
		return api.get(userId, "/api/me/solves" + query);
	}

	/** 409 problem+json의 code 확장 속성 (사람이 정함, 2026-11-26 "code 속성 추가"). */
	private static String code(MvcTestResult result) {
		JsonNode code = TestApi.json(result).get("code");
		return code == null ? "(없음)" : code.asString();
	}

	private static List<Long> ids(JsonNode page) {
		List<Long> ids = new ArrayList<>();
		page.get("content").forEach(item -> ids.add(item.get("id").asLong()));
		return ids;
	}

	private int memberDayCount(long studyId, long userId) {
		return jdbc.sql("select count(*) from studybot.member_days where study_id = :studyId and user_id = :userId")
			.param("studyId", studyId)
			.param("userId", userId)
			.query(Integer.class)
			.single();
	}

	// --- 내 풀이 목록 ---

	@Test
	@DisplayName("내 풀이: 내 것만, 확인 대기 포함, 수신 시각이 늦은 것부터. 확인 대기는 문제 없이 원본 경로를 준다")
	void listsOnlyMySolvesNewestFirst() {
		long alice = rows.newUser("s10-list-a");
		long bob = rows.newUser("s10-list-b");
		long selfReport = rows.solve(alice, "PROGRAMMERS", "12916", seoul(NOV_20, 9, 0));
		long confirmed = rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_20, 10, 0));
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 12, 0), "algorithm/codetree/별 찍기/Main.java");
		rows.solve(bob, "BOJ", "1000", seoul(NOV_20, 11, 0));

		MvcTestResult result = mySolves(alice, "");

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = TestApi.json(result);
		assertThat(ids(body)).containsExactly(pending, confirmed, selfReport);
		JsonNode pendingItem = body.get("content").get(0);
		assertThat(pendingItem.get("status").asString()).isEqualTo("PENDING_IDENTIFICATION");
		assertThat(pendingItem.get("source").asString()).isEqualTo("GITHUB");
		assertThat(pendingItem.get("platform").isNull()).isTrue();
		assertThat(pendingItem.get("problemNo").isNull()).isTrue();
		assertThat(pendingItem.get("rawPath").asString()).isEqualTo("algorithm/codetree/별 찍기/Main.java");
		assertThat(pendingItem.get("receivedAt").asString()).isEqualTo("2026-11-20T03:00:00Z");
		JsonNode confirmedItem = body.get("content").get(1);
		assertThat(confirmedItem.get("status").asString()).isEqualTo("CONFIRMED");
		assertThat(confirmedItem.get("platform").asString()).isEqualTo("BOJ");
		assertThat(confirmedItem.get("problemNo").asString()).isEqualTo("1000");
		assertThat(confirmedItem.get("title").asString()).isEqualTo("A+B");
		assertThat(body.get("content").get(2).get("source").asString()).isEqualTo("SELF_REPORT");
		assertThat(body.get("page").get("totalElements").asLong()).isEqualTo(3);
	}

	@Test
	@DisplayName("내 풀이: from 이상 to 미만으로 거른다 (오프셋 있는 ISO 8601)")
	void filtersByHalfOpenRange() {
		long alice = rows.newUser("s10-range");
		rows.solve(alice, "BOJ", "1000", seoul(NOV_20, 9, 59));
		long inside = rows.solve(alice, "BOJ", "1001", seoul(NOV_20, 10, 0));
		rows.solve(alice, "BOJ", "1002", seoul(NOV_20, 12, 0));

		MvcTestResult result = api.getWithParams(alice, "/api/me/solves", "from", "2026-11-20T10:00:00+09:00", "to",
				"2026-11-20T12:00:00+09:00");

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(ids(TestApi.json(result))).containsExactly(inside);
	}

	@Test
	@DisplayName("내 풀이: 페이지로 나눠 준다")
	void paginates() {
		long alice = rows.newUser("s10-page");
		long oldest = rows.solve(alice, "BOJ", "1000", seoul(NOV_20, 9, 0));
		rows.solve(alice, "BOJ", "1001", seoul(NOV_20, 10, 0));
		rows.solve(alice, "BOJ", "1002", seoul(NOV_20, 11, 0));

		JsonNode second = TestApi.json(mySolves(alice, "?page=1&size=2"));

		assertThat(ids(second)).containsExactly(oldest);
		assertThat(second.get("page").get("totalElements").asLong()).isEqualTo(3);
		assertThat(second.get("page").get("size").asInt()).isEqualTo(2);
	}

	@Test
	@DisplayName("목록 API는 페이지 크기 상한이 있다: size 51 → 400 (50까지)")
	void pageSizeIsCapped() {
		long alice = rows.newUser("s10-cap");

		assertThat(mySolves(alice, "?size=51")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mySolves(alice, "?size=0")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mySolves(alice, "?page=-1")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mySolves(alice, "?size=50")).hasStatus(HttpStatus.OK);
	}

	@Test
	@DisplayName("내 풀이: status로 상태를 거른다. 확인 대기만 모아 볼 수 있다 (사람이 정함, 2026-11-26 \"status 필터 추가\")")
	void filtersByStatus() {
		long alice = rows.newUser("s10-status");
		long bob = rows.newUser("s10-status-other");
		long confirmed = rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_20, 10, 0));
		long olderPending = rows.pendingSolve(alice, seoul(NOV_20, 9, 0), "algorithm/a.py");
		long newerPending = rows.pendingSolve(alice, seoul(NOV_20, 11, 0), "algorithm/b.py");
		rows.pendingSolve(bob, seoul(NOV_20, 12, 0), "algorithm/c.py");

		JsonNode pending = TestApi.json(api.getWithParams(alice, "/api/me/solves", "status", "PENDING_IDENTIFICATION"));
		JsonNode confirmedOnly = TestApi.json(api.getWithParams(alice, "/api/me/solves", "status", "CONFIRMED"));
		JsonNode pendingInRange = TestApi.json(api.getWithParams(alice, "/api/me/solves", "status",
				"PENDING_IDENTIFICATION", "from", "2026-11-20T10:00:00+09:00", "to", "2026-11-21T00:00:00+09:00"));
		JsonNode firstPage = TestApi.json(api.getWithParams(alice, "/api/me/solves", "status", "PENDING_IDENTIFICATION",
				"size", "1"));

		assertThat(ids(pending)).containsExactly(newerPending, olderPending);
		assertThat(ids(confirmedOnly)).containsExactly(confirmed);
		assertThat(ids(pendingInRange)).containsExactly(newerPending);
		assertThat(ids(firstPage)).containsExactly(newerPending);
		assertThat(firstPage.get("page").get("totalElements").asLong()).isEqualTo(2);
	}

	@Test
	@DisplayName("내 풀이: 모르는 status는 400, 대소문자를 가린다. 빈 status는 거르지 않은 것과 같다")
	void rejectsUnknownStatus() {
		long alice = rows.newUser("s10-bad-status");
		long confirmed = rows.solve(alice, "BOJ", "1000", seoul(NOV_20, 10, 0));
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 11, 0), "algorithm/a.py");

		assertThat(api.getWithParams(alice, "/api/me/solves", "status", "DONE")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(api.getWithParams(alice, "/api/me/solves", "status", "pending_identification"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(ids(TestApi.json(api.getWithParams(alice, "/api/me/solves", "status", ""))))
			.containsExactly(pending, confirmed);
	}

	@Test
	@DisplayName("내 풀이: 날짜 형식이 틀리면 400, 로그인하지 않으면 401")
	void rejectsBadRangeAndAnonymous() {
		long alice = rows.newUser("s10-bad-range");

		assertThat(mySolves(alice, "?from=yesterday")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(api.getAnonymous("/api/me/solves")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// --- 확인 대기 지정 ---

	@Test
	@DisplayName("T26: 확인 대기 풀이에 문제를 지정하면 원래 수신 시각 기준의 스터디 일(11/20)에 반영된다")
	void t26IdentifiedPendingSolveCountsOnTheOriginalDay() {
		long alice = rows.newUser("s10-t26");
		TestStudy study = rows.standardStudy(alice);
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/a.py");

		MvcTestResult result = identify(alice, pending, BOJ_1000);

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode body = TestApi.json(result);
		assertThat(body.get("id").asLong()).isEqualTo(pending);
		assertThat(body.get("status").asString()).isEqualTo("CONFIRMED");
		assertThat(body.get("platform").asString()).isEqualTo("BOJ");
		assertThat(body.get("problemNo").asString()).isEqualTo("1000");
		assertThat(body.get("receivedAt").asString()).isEqualTo("2026-11-20T14:50:00Z");
		assertThat(rows.solveRecord(pending)).isEqualTo(new SolveRecordRow("CONFIRMED", "BOJ", "1000"));
		assertThat(rows.studyDaySolves(study.studyId(), alice)).extracting(StudyDaySolveRow::studyDay,
				StudyDaySolveRow::solveRecordId, StudyDaySolveRow::platform, StudyDaySolveRow::problemNo)
			.containsExactly(tuple(NOV_20, pending, "BOJ", "1000"));
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isEqualTo(1);
		assertThat(rows.memberDay(study.studyId(), alice, NOV_22)).isEmpty();
	}

	@Test
	@DisplayName("T26: 유예 안에 받은 확인 대기는 전날이 미달이면 R4대로 전날(11/20)에 반영된다")
	void t26PendingSolveInGraceGoesToPreviousDay() {
		long alice = rows.newUser("s10-t26-grace");
		TestStudy study = rows.standardStudy(alice);
		long pending = rows.pendingSolve(alice, seoul(NOV_21, 0, 20), "algorithm/a.py");

		assertThat(identify(alice, pending, BOJ_1000)).hasStatus(HttpStatus.OK);

		assertThat(rows.studyDaySolves(study.studyId(), alice)).extracting(StudyDaySolveRow::studyDay)
			.containsExactly(NOV_20);
	}

	@Test
	@DisplayName("T26: 유예 안에 받았어도 전날이 이미 달성이면 받은 날(11/21)에 반영된다")
	void t26PendingSolveInGraceAfterPreviousDayDone() {
		long alice = rows.newUser("s10-t26-done");
		TestStudy study = rows.standardStudy(alice);
		rows.putMemberDay(study.studyId(), alice, NOV_20, 1, null, null);
		long pending = rows.pendingSolve(alice, seoul(NOV_21, 0, 20), "algorithm/a.py");

		assertThat(identify(alice, pending, BOJ_1000)).hasStatus(HttpStatus.OK);

		assertThat(rows.studyDaySolves(study.studyId(), alice)).extracting(StudyDaySolveRow::studyDay)
			.containsExactly(NOV_21);
	}

	@Test
	@DisplayName("유예 안에 받은 확인 대기의 전날이 FAIL로 판정됐으면 409이고 그대로다. 받은 시각에는 전날로 갔을 풀이다 "
			+ "(사람이 정함, 2026-11-25 \"FAIL이면 409\")")
	void graceSolveWithPreviousDayFailedIsConflict() {
		long alice = rows.newUser("s10-grace-fail");
		TestStudy study = rows.standardStudy(alice);
		long pending = rows.pendingSolve(alice, seoul(NOV_21, 0, 20), "algorithm/a.py");
		rows.markJudged(study.studyId(), alice, NOV_20, "FAIL");

		MvcTestResult result = identify(alice, pending, BOJ_1000);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(TestApi.json(result).get("detail").asString()).contains("이의");
		assertThat(code(result)).isEqualTo("STUDY_DAY_JUDGED");
		assertThat(rows.solveRecord(pending).status()).isEqualTo("PENDING_IDENTIFICATION");
		assertThat(rows.studyDaySolves(study.studyId(), alice)).isEmpty();
		assertThat(rows.memberDay(study.studyId(), alice, NOV_21)).isEmpty();
	}

	@Test
	@DisplayName("유예 안에 받은 확인 대기의 전날이 성공·면제권 면제로 판정됐으면 받은 날(11/21)에 반영된다")
	void graceSolveWithPreviousDayJudgedOtherwiseCountsOnReceivedDay() {
		long alice = rows.newUser("s10-grace-success");
		TestStudy success = rows.standardStudy(alice);
		TestStudy ticket = rows.standardStudy(alice);
		rows.putMemberDay(success.studyId(), alice, NOV_20, 1, "SUCCESS", null);
		rows.putMemberDay(ticket.studyId(), alice, NOV_20, 0, "EXEMPT_TICKET", seoul(NOV_20, 9, 0));
		long pending = rows.pendingSolve(alice, seoul(NOV_21, 0, 20), "algorithm/a.py");

		assertThat(identify(alice, pending, BOJ_1000)).hasStatus(HttpStatus.OK);

		assertThat(rows.studyDaySolves(success.studyId(), alice)).extracting(StudyDaySolveRow::studyDay)
			.containsExactly(NOV_21);
		assertThat(rows.studyDaySolves(ticket.studyId(), alice)).extracting(StudyDaySolveRow::studyDay)
			.containsExactly(NOV_21);
	}

	@Test
	@DisplayName("같은 확인 대기를 동시에 두 번 지정해도 한 번만 바뀐다 (하나는 200, 하나는 409)")
	void concurrentIdentificationChangesOnce() throws Exception {
		long alice = rows.newUser("s10-concurrent");
		TestStudy study = rows.standardStudy(alice);
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/a.py");
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (String problemNo : List.of("1000", "1001")) {
				results.add(executor.submit(() -> {
					start.await();
					return identify(alice, pending, "{\"platform\": \"BOJ\", \"problemNo\": \"" + problemNo + "\"}")
						.getResponse()
						.getStatus();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : results) {
				statuses.add(future.get(30, TimeUnit.SECONDS));
			}

			assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(rows.studyDaySolves(study.studyId(), alice)).hasSize(1);
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isEqualTo(1);
	}

	@Test
	@DisplayName("이미 판정된 날의 확인 대기에 문제를 지정하면 409이고 아무것도 바뀌지 않는다 (이의 안내)")
	void judgedDayIsConflictAndNothingChanges() {
		long alice = rows.newUser("s10-judged");
		TestStudy study = rows.standardStudy(alice);
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/a.py");
		rows.markJudged(study.studyId(), alice, NOV_20, "FAIL");
		MemberDayRow before = rows.memberDay(study.studyId(), alice, NOV_20).orElseThrow();

		MvcTestResult result = identify(alice, pending, BOJ_1000);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(TestApi.json(result).get("detail").asString()).contains("이의");
		assertThat(code(result)).isEqualTo("STUDY_DAY_JUDGED");
		assertThat(rows.solveRecord(pending)).isEqualTo(new SolveRecordRow("PENDING_IDENTIFICATION", null, null));
		assertThat(rows.studyDaySolves(study.studyId(), alice)).isEmpty();
		assertThat(rows.memberDay(study.studyId(), alice, NOV_20)).contains(before);
	}

	@Test
	@DisplayName("스터디 두 곳 중 한 곳만 그날이 판정됐어도 409이고 두 스터디 모두 그대로다 (사람이 정함, 2026-11-25)")
	void judgedInOneOfTwoStudiesIsConflictForAll() {
		long alice = rows.newUser("s10-two");
		TestStudy open = rows.standardStudy(alice);
		TestStudy judged = rows.standardStudy(alice);
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/a.py");
		rows.markJudged(judged.studyId(), alice, NOV_20, "FAIL");

		MvcTestResult result = identify(alice, pending, BOJ_1000);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(result)).isEqualTo("STUDY_DAY_JUDGED");
		assertThat(rows.solveRecord(pending).status()).isEqualTo("PENDING_IDENTIFICATION");
		assertThat(rows.studyDaySolves(open.studyId(), alice)).isEmpty();
		assertThat(memberDayCount(open.studyId(), alice)).isZero();
		assertThat(rows.studyDaySolves(judged.studyId(), alice)).isEmpty();
	}

	@Test
	@DisplayName("남의 풀이에 문제를 지정할 수 없다 (T19: 404, 그대로)")
	void t19CannotIdentifySomeoneElsesSolve() {
		long alice = rows.newUser("s10-owner");
		long eve = rows.newUser("s10-eve");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), eve, NOV_1);
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/a.py");

		assertThat(identify(eve, pending, BOJ_1000)).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(rows.solveRecord(pending).status()).isEqualTo("PENDING_IDENTIFICATION");
		assertThat(rows.studyDaySolves(study.studyId(), alice)).isEmpty();
		assertThat(rows.studyDaySolves(study.studyId(), eve)).isEmpty();
	}

	@Test
	@DisplayName("T19: 내 풀이 목록에 남의 풀이가 없고, 없는 풀이·로그인하지 않은 지정은 404·401")
	void t19MissingSolveAndAnonymous() {
		long alice = rows.newUser("s10-t19-list");
		long eve = rows.newUser("s10-t19-other");
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/a.py");

		assertThat(ids(TestApi.json(mySolves(eve, "")))).doesNotContain(pending);
		assertThat(identify(alice, Long.MAX_VALUE, BOJ_1000)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(api.patchJsonAnonymous("/api/me/solves/{id}", BOJ_1000, pending))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(rows.solveRecord(pending).status()).isEqualTo("PENDING_IDENTIFICATION");
	}

	@Test
	@DisplayName("이미 문제를 아는 풀이에는 지정할 수 없다 (409). 같은 확인 대기를 두 번 지정해도 두 번째는 409")
	void onlyPendingSolvesCanBeIdentified() {
		long alice = rows.newUser("s10-twice");
		TestStudy study = rows.standardStudy(alice);
		long confirmed = rows.githubSolve(alice, "BOJ", "2000", "제목", seoul(NOV_20, 10, 0));
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/a.py");

		MvcTestResult notPending = identify(alice, confirmed, BOJ_1000);
		assertThat(notPending).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(notPending)).isEqualTo("SOLVE_NOT_PENDING");
		assertThat(identify(alice, pending, BOJ_1000)).hasStatus(HttpStatus.OK);
		MvcTestResult again = identify(alice, pending, "{\"platform\": \"BOJ\", \"problemNo\": \"1001\"}");
		assertThat(again).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(again)).isEqualTo("SOLVE_NOT_PENDING");

		assertThat(rows.solveRecord(confirmed)).isEqualTo(new SolveRecordRow("CONFIRMED", "BOJ", "2000"));
		assertThat(rows.solveRecord(pending)).isEqualTo(new SolveRecordRow("CONFIRMED", "BOJ", "1000"));
		assertThat(rows.studyDaySolves(study.studyId(), alice)).extracting(StudyDaySolveRow::problemNo)
			.containsExactly("1000");
	}

	@Test
	@DisplayName("같은 커밋에 그 문제의 기록이 이미 있으면 409이고 그대로다 (UNIQUE 위반 500 대신)")
	void sameProblemInTheSameCommitIsConflict() {
		long alice = rows.newUser("s10-same-commit");
		TestStudy study = rows.standardStudy(alice);
		long confirmed = rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_20, 23, 50));
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/a.py");
		jdbc.sql("""
				update studybot.solve_records set commit_sha = (select commit_sha from studybot.solve_records where id = :confirmed)
				where id = :pending
				""").param("confirmed", confirmed).param("pending", pending).update();

		MvcTestResult result = identify(alice, pending, BOJ_1000);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(result)).isEqualTo("PROBLEM_ALREADY_IN_COMMIT");
		assertThat(rows.solveRecord(pending).status()).isEqualTo("PENDING_IDENTIFICATION");
		assertThat(rows.studyDaySolves(study.studyId(), alice)).isEmpty();
		assertThat(identify(alice, pending, "{\"platform\": \"BOJ\", \"problemNo\": \"1001\"}")).hasStatus(HttpStatus.OK);
	}

	@Test
	@DisplayName("같은 커밋의 확인 대기 둘을 동시에 같은 문제로 지정하면 나중 것은 409다 (UNIQUE 경합이 500이 되지 않음, "
			+ "CodeRabbit PR #26 리뷰)")
	void concurrentSameProblemInTheSameCommitIsConflict() throws Exception {
		long alice = rows.newUser("s10-commit-race");
		long first = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/a.py");
		long second = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/b.py");
		jdbc.sql("""
				update studybot.solve_records set commit_sha = (select commit_sha from studybot.solve_records where id = :first)
				where id = :second
				""").param("first", first).param("second", second).update();
		CountDownLatch firstApplied = new CountDownLatch(1);
		CountDownLatch commitFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> identifyingFirst = executor.submit(() -> new TransactionTemplate(transactionManager)
				.executeWithoutResult(status -> {
					mySolveService.identify(alice, first, new ProblemRef(Platform.BOJ, "1000"));
					firstApplied.countDown();
					awaitQuietly(commitFirst);
				}));
			assertThat(firstApplied.await(10, TimeUnit.SECONDS)).as("첫 지정이 커밋 전까지 반영됨").isTrue();

			Future<MvcTestResult> identifyingSecond = executor.submit(() -> identify(alice, second, BOJ_1000));
			await().atMost(Duration.ofSeconds(10)).until(this::someoneWaitsForSolveRecordLock);
			commitFirst.countDown();

			identifyingFirst.get(10, TimeUnit.SECONDS);
			MvcTestResult result = identifyingSecond.get(10, TimeUnit.SECONDS);
			assertThat(result).hasStatus(HttpStatus.CONFLICT);
			assertThat(code(result)).isEqualTo("PROBLEM_ALREADY_IN_COMMIT");
		}
		finally {
			commitFirst.countDown();
			executor.shutdownNow();
		}
		assertThat(rows.solveRecord(first)).isEqualTo(new SolveRecordRow("CONFIRMED", "BOJ", "1000"));
		assertThat(rows.solveRecord(second)).isEqualTo(new SolveRecordRow("PENDING_IDENTIFICATION", null, null));
	}

	/** 다른 연결이 solve_records의 잠금(여기서는 UNIQUE 인덱스의 커밋 전 행)을 기다리고 있는지 (pg_stat_activity). */
	private boolean someoneWaitsForSolveRecordLock() {
		return jdbc.sql("""
				select count(*) from pg_stat_activity
				where datname = current_database() and pid <> pg_backend_pid()
					and wait_event_type = 'Lock' and query ilike '%solve_records%'
				""").query(Long.class).single() > 0;
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"problemNo\": \"1000\"}", "{\"platform\": \"BOJ\"}",
			"{\"platform\": \"CODEFORCES\", \"problemNo\": \"1000\"}", "{\"platform\": \"BOJ\", \"problemNo\": \"01000\"}",
			"{\"platform\": \"BOJ\", \"problemNo\": \"abc\"}", "{\"platform\": \"BOJ\", \"problemNo\": \"\"}",
			"{\"platform\": \"BOJ\", \"problemNo\": \"12345678901\"}" })
	@DisplayName("지정할 문제가 없거나 형식이 틀리면 400이고 그대로다")
	void rejectsInvalidProblem(String body) {
		long alice = rows.newUser("s10-invalid");
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 23, 50), "algorithm/a.py");

		assertThat(identify(alice, pending, body)).hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(rows.solveRecord(pending).status()).isEqualTo("PENDING_IDENTIFICATION");
	}

	@Test
	@DisplayName("S10 주의할 것: 탈퇴 전에 받았어도 탈퇴한 뒤(다시 가입하기 전) 지정하면 그 스터디에는 세지 않는다 (R14)")
	void identifiedAfterLeavingIsNotCounted() {
		long alice = rows.newUser("s10-left");
		TestStudy study = rows.standardStudy(alice);
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 20, 0), "algorithm/a.py");
		rows.leave(study.membershipId(), seoul(NOV_21, 10, 0));

		assertThat(identify(alice, pending, BOJ_1000)).hasStatus(HttpStatus.OK);

		assertThat(rows.solveRecord(pending).status()).isEqualTo("CONFIRMED");
		assertThat(rows.studyDaySolves(study.studyId(), alice)).isEmpty();
	}

	@Test
	@DisplayName("S10 주의할 것: 가입 전에 받았어도 가입한 뒤 지정하면 받은 날(판정하지 않는 날)에 기록한다")
	void identifiedAfterJoiningIsRecordedOnTheOriginalDay() {
		long alice = rows.newUser("s10-joined");
		long owner = rows.newUser("s10-joined-owner");
		TestStudy study = rows.standardStudy(owner);
		long pending = rows.pendingSolve(alice, seoul(NOV_20, 20, 0), "algorithm/a.py");
		rows.join(study.studyId(), alice, seoul(NOV_21, 10, 0), NOV_22);

		assertThat(identify(alice, pending, BOJ_1000)).hasStatus(HttpStatus.OK);

		assertThat(rows.studyDaySolves(study.studyId(), alice)).extracting(StudyDaySolveRow::studyDay)
			.containsExactly(NOV_20);
		assertThat(rows.solvedCount(study.studyId(), alice, NOV_20)).isEqualTo(1);
	}

	@Test
	@DisplayName("스터디가 없는 사용자도 지정할 수 있다 (풀이 기록만 확인됨)")
	void identifiesWithoutStudies() {
		long alice = rows.newUser("s10-alone");
		Instant receivedAt = seoul(NOV_20, 20, 0);
		long pending = rows.pendingSolve(alice, receivedAt, "algorithm/a.py");

		assertThat(identify(alice, pending, "{\"platform\": \"LEETCODE\", \"problemNo\": \"1\"}"))
			.hasStatus(HttpStatus.OK);

		assertThat(rows.solveRecord(pending)).isEqualTo(new SolveRecordRow("CONFIRMED", "LEETCODE", "1"));
	}

}
