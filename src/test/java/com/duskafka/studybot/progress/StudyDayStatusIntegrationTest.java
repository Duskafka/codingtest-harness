package com.duskafka.studybot.progress;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.SEOUL;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 오늘 현황과 특정 날 상세 (S10 만들 것 1·2, 수용 기준 1·T19). {@code GET /api/studies/{id}/today},
 * {@code GET /api/studies/{id}/days/{date}}.
 *
 * <p>풀이는 S09 배정({@link StudyDayAssignment})을 풀이 기록을 넣는 쪽처럼 트랜잭션 안에서 불러 반영한다. 그래서 현황이 배정 규칙(R4)과
 * 판정 규칙(R8·R9)대로 보이는지를 3.5 판정 예시로 확인한다.
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 1기 11-01~11-28 ({@link StudyDayRows#standardStudy}).
 */
@IntegrationTest
class StudyDayStatusIntegrationTest {

	private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

	private static final LocalDate NOV_19 = LocalDate.of(2026, 11, 19);

	/** 금요일. */
	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private StudyDayAssignment assignment;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private StudyDayRows rows;

	private TestApi api;

	private TransactionTemplate transaction;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		api = new TestApi(mvc, jdbc);
		transaction = new TransactionTemplate(transactionManager);
	}

	/** 수동 인증 출처의 풀이 기록을 넣고 배정한다. */
	private long solveAndAssign(long userId, String problemNo, Instant receivedAt) {
		long solveId = rows.solve(userId, "BOJ", problemNo, receivedAt);
		assign(solveId, userId, problemNo, receivedAt);
		return solveId;
	}

	private void assign(long solveId, long userId, String problemNo, Instant receivedAt) {
		transaction.executeWithoutResult(
				status -> assignment.assign(new SolveToAssign(solveId, userId, "BOJ", problemNo, receivedAt)));
	}

	private MvcTestResult today(long userId, long studyId) {
		return api.get(userId, "/api/studies/{studyId}/today", studyId);
	}

	private MvcTestResult day(long userId, long studyId, LocalDate date) {
		return api.get(userId, "/api/studies/{studyId}/days/{date}", studyId, date.toString());
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.OK);
		return TestApi.json(result);
	}

	private static JsonNode member(JsonNode body, long userId) {
		JsonNode members = body.get("members");
		for (int i = 0; i < members.size(); i++) {
			if (members.get(i).get("userId").asLong() == userId) {
				return members.get(i);
			}
		}
		throw new AssertionError("멤버가 없다: " + userId + " in " + members);
	}

	private static List<Long> memberIds(JsonNode body) {
		List<Long> ids = new ArrayList<>();
		body.get("members").forEach(member -> ids.add(member.get("userId").asLong()));
		return ids;
	}

	private static String status(JsonNode body, long userId) {
		return member(body, userId).get("status").asString();
	}

	private void setClock(Instant instant) {
		clock.setInstant(instant);
	}

	// --- 3.5 판정 예시 ---

	@Test
	@DisplayName("3.5: 11/20 23:50 수신 → 11/20 완료. 풀지 않은 멤버는 남음. 하루의 시각·목표·요약을 함께 준다")
	void solvedBeforeBoundaryIsDone() {
		long alice = rows.newUser("s10-alice");
		long bob = rows.newUser("s10-bob");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), bob, NOV_1);
		solveAndAssign(alice, "1000", seoul(NOV_20, 23, 50));
		setClock(seoul(NOV_20, 23, 55));

		JsonNode body = ok(today(alice, study.studyId()));

		assertThat(body.get("studyId").asLong()).isEqualTo(study.studyId());
		assertThat(body.get("studyDay").asString()).isEqualTo("2026-11-20");
		assertThat(body.get("today").asBoolean()).isTrue();
		assertThat(body.get("startsAt").asString()).isEqualTo("2026-11-19T15:00:00Z");
		assertThat(body.get("endsAt").asString()).isEqualTo("2026-11-20T15:00:00Z");
		assertThat(body.get("graceEndsAt").asString()).isEqualTo("2026-11-20T15:30:00Z");
		assertThat(body.get("dailyGoal").asInt()).isEqualTo(1);
		assertThat(body.get("restDay").asBoolean()).isFalse();
		assertThat(body.get("inSeason").asBoolean()).isTrue();
		JsonNode me = member(body, alice);
		assertThat(me.get("status").asString()).isEqualTo("DONE");
		assertThat(me.get("solvedCount").asInt()).isEqualTo(1);
		assertThat(me.get("exemptionUsed").asBoolean()).isFalse();
		assertThat(me.get("exemptReason").isNull()).isTrue();
		assertThat(me.get("result").isNull()).isTrue();
		assertThat(me.get("role").asString()).isEqualTo("MEMBER");
		assertThat(me.get("login").asString()).startsWith("s10-alice-");
		assertThat(status(body, bob)).isEqualTo("REMAINING");
		assertThat(member(body, bob).get("solvedCount").asInt()).isZero();
		assertThat(body.get("summary").get("done").asInt()).isEqualTo(1);
		assertThat(body.get("summary").get("remaining").asInt()).isEqualTo(1);
		assertThat(body.get("summary").get("exempt").asInt()).isZero();
	}

	@Test
	@DisplayName("3.5: 11/21 00:20 수신, 11/20 미달 → 11/20 완료 (유예). 오늘(11/21)은 남음")
	void graceSolveCompletesPreviousDay() {
		long alice = rows.newUser("s10-grace");
		TestStudy study = rows.standardStudy(alice);
		solveAndAssign(alice, "1000", seoul(NOV_21, 0, 20));
		setClock(seoul(NOV_21, 0, 25));

		JsonNode previous = ok(day(alice, study.studyId(), NOV_20));
		JsonNode today = ok(today(alice, study.studyId()));

		assertThat(previous.get("studyDay").asString()).isEqualTo("2026-11-20");
		assertThat(previous.get("today").asBoolean()).isFalse();
		assertThat(status(previous, alice)).isEqualTo("DONE");
		assertThat(today.get("studyDay").asString()).isEqualTo("2026-11-21");
		assertThat(status(today, alice)).isEqualTo("REMAINING");
	}

	@Test
	@DisplayName("3.5: 11/21 00:20 수신, 11/20 이미 달성 → 11/21 풀이로 집계")
	void graceSolveAfterPreviousDayDoneCountsToday() {
		long alice = rows.newUser("s10-grace-done");
		TestStudy study = rows.standardStudy(alice);
		solveAndAssign(alice, "1000", seoul(NOV_20, 22, 0));
		solveAndAssign(alice, "1001", seoul(NOV_21, 0, 20));
		setClock(seoul(NOV_21, 0, 25));

		JsonNode previous = ok(day(alice, study.studyId(), NOV_20));
		JsonNode today = ok(today(alice, study.studyId()));

		assertThat(member(previous, alice).get("solvedCount").asInt()).isEqualTo(1);
		assertThat(status(today, alice)).isEqualTo("DONE");
		assertThat(member(today, alice).get("solvedCount").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("3.5: 11/21 00:35 수신, 11/20 미달 → 유예 밖이라 11/21 풀이. 11/20은 남음 (판정 전이라 결과 없음)")
	void solveAfterGraceCountsToday() {
		long alice = rows.newUser("s10-after-grace");
		TestStudy study = rows.standardStudy(alice);
		solveAndAssign(alice, "1000", seoul(NOV_21, 0, 35));
		setClock(seoul(NOV_21, 0, 36));

		JsonNode previous = ok(day(alice, study.studyId(), NOV_20));
		JsonNode today = ok(today(alice, study.studyId()));

		assertThat(status(previous, alice)).isEqualTo("REMAINING");
		assertThat(member(previous, alice).get("result").isNull()).isTrue();
		assertThat(status(today, alice)).isEqualTo("DONE");
	}

	@Test
	@DisplayName("3.5: 11/20 면제권 사용 후 23:00 풀이 → 완료, 면제권 사용 표시는 남는다 (R8)")
	void ticketThenSolveIsDone() {
		long alice = rows.newUser("s10-ticket-solve");
		TestStudy study = rows.standardStudy(alice);
		rows.putMemberDay(study.studyId(), alice, NOV_20, 0, null, seoul(NOV_20, 10, 0));
		solveAndAssign(alice, "1000", seoul(NOV_20, 23, 0));
		setClock(seoul(NOV_20, 23, 5));

		JsonNode me = member(ok(today(alice, study.studyId())), alice);

		assertThat(me.get("status").asString()).isEqualTo("DONE");
		assertThat(me.get("exemptionUsed").asBoolean()).isTrue();
		assertThat(me.get("exemptReason").isNull()).isTrue();
	}

	@Test
	@DisplayName("3.5: 11/20 면제권 사용, 풀이 없음 → 면제 (면제권)")
	void ticketWithoutSolveIsExempt() {
		long alice = rows.newUser("s10-ticket");
		TestStudy study = rows.standardStudy(alice);
		rows.putMemberDay(study.studyId(), alice, NOV_20, 0, null, seoul(NOV_20, 10, 0));
		setClock(seoul(NOV_20, 23, 5));

		JsonNode body = ok(today(alice, study.studyId()));

		JsonNode me = member(body, alice);
		assertThat(me.get("status").asString()).isEqualTo("EXEMPT");
		assertThat(me.get("exemptReason").asString()).isEqualTo("TICKET");
		assertThat(me.get("exemptionUsed").asBoolean()).isTrue();
		assertThat(body.get("summary").get("exempt").asInt()).isEqualTo(1);
		assertThat(body.get("summary").get("done").asInt()).isZero();
		assertThat(body.get("summary").get("remaining").asInt()).isZero();
	}

	@Test
	@DisplayName("3.5: 같은 날 같은 문제를 두 번 커밋 → 1문제로 셈, 최근 풀이도 하나")
	void sameProblemTwiceCountsOnce() {
		long alice = rows.newUser("s10-twice");
		TestStudy study = rows.standardStudy(alice);
		solveAndAssign(alice, "1000", seoul(NOV_20, 10, 0));
		solveAndAssign(alice, "1000", seoul(NOV_20, 11, 0));
		setClock(seoul(NOV_20, 12, 0));

		JsonNode body = ok(today(alice, study.studyId()));

		assertThat(member(body, alice).get("solvedCount").asInt()).isEqualTo(1);
		assertThat(body.get("recentSolves").size()).isEqualTo(1);
	}

	@Test
	@DisplayName("3.5: 기준 06:00 스터디에서 새벽 03:00 풀이 → 전날(11/20)로 집계. 그 시각의 오늘도 11/20이다")
	void earlyMorningCountsPreviousDayWithLateBoundary() {
		long alice = rows.newUser("s10-six");
		TestStudy study = rows.standardStudy(alice, SEOUL, LocalTime.of(6, 0), 30, "");
		solveAndAssign(alice, "1000", seoul(NOV_21, 3, 0));
		setClock(seoul(NOV_21, 3, 10));

		JsonNode body = ok(today(alice, study.studyId()));

		assertThat(body.get("studyDay").asString()).isEqualTo("2026-11-20");
		assertThat(body.get("endsAt").asString()).isEqualTo("2026-11-20T21:00:00Z");
		assertThat(status(body, alice)).isEqualTo("DONE");
	}

	@Test
	@DisplayName("3.5: 11/20에 가입 → 11/20은 판정하지 않음(요약에서 빠짐), 11/21부터 판정")
	void joinedTodayIsNotJudgedUntilTomorrow() {
		long alice = rows.newUser("s10-old");
		long carol = rows.newUser("s10-new");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), carol, seoul(NOV_20, 10, 0), NOV_21);

		setClock(seoul(NOV_20, 12, 0));
		JsonNode joinedDay = ok(today(carol, study.studyId()));
		setClock(seoul(NOV_21, 12, 0));
		JsonNode nextDay = ok(today(carol, study.studyId()));

		assertThat(status(joinedDay, carol)).isEqualTo("NOT_JUDGED");
		assertThat(joinedDay.get("summary").get("remaining").asInt()).isEqualTo(1);
		assertThat(status(nextDay, carol)).isEqualTo("REMAINING");
		assertThat(nextDay.get("summary").get("remaining").asInt()).isEqualTo(2);
	}

	// --- 그 밖의 규칙 ---

	@Test
	@DisplayName("R9: 쉬는 날은 풀었어도 면제 (쉬는 날)")
	void restDayIsExempt() {
		long alice = rows.newUser("s10-rest");
		TestStudy study = rows.standardStudy(alice, SEOUL, LocalTime.MIDNIGHT, 30, "FRIDAY");
		solveAndAssign(alice, "1000", seoul(NOV_20, 10, 0));
		setClock(seoul(NOV_20, 12, 0));

		JsonNode body = ok(today(alice, study.studyId()));

		assertThat(body.get("restDay").asBoolean()).isTrue();
		assertThat(status(body, alice)).isEqualTo("EXEMPT");
		assertThat(member(body, alice).get("exemptReason").asString()).isEqualTo("REST_DAY");
		assertThat(member(body, alice).get("solvedCount").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("R14: 오늘 탈퇴한 멤버도 그날까지 판정하니 보인다. 다음 날부터는 보이지 않는다")
	void memberWhoLeftTodayIsShownUntilTheEndOfTheDay() {
		long alice = rows.newUser("s10-stay");
		long dave = rows.newUser("s10-leave");
		TestStudy study = rows.standardStudy(alice);
		long daveMembership = rows.join(study.studyId(), dave, NOV_1);
		rows.leave(daveMembership, seoul(NOV_20, 15, 0));

		setClock(seoul(NOV_20, 20, 0));
		JsonNode leftDay = ok(today(alice, study.studyId()));
		setClock(seoul(NOV_21, 12, 0));
		JsonNode nextDay = ok(today(alice, study.studyId()));
		JsonNode leftDayLater = ok(day(alice, study.studyId(), NOV_20));

		assertThat(status(leftDay, dave)).isEqualTo("REMAINING");
		assertThat(memberIds(nextDay)).containsExactly(alice);
		assertThat(status(leftDayLater, dave)).isEqualTo("REMAINING");
	}

	@Test
	@DisplayName("R14: 탈퇴한 날 다시 가입한 사용자는 한 번만 보이고, 그날도 판정한다")
	void rejoinedOnTheSameDayIsShownOnceAndJudged() {
		long alice = rows.newUser("s10-rejoin");
		TestStudy study = rows.standardStudy(alice);
		rows.leave(study.membershipId(), seoul(NOV_20, 9, 0));
		rows.join(study.studyId(), alice, seoul(NOV_20, 10, 0), NOV_21);
		setClock(seoul(NOV_20, 12, 0));

		JsonNode body = ok(today(alice, study.studyId()));

		assertThat(memberIds(body)).containsExactly(alice);
		assertThat(status(body, alice)).isEqualTo("REMAINING");
	}

	@Test
	@DisplayName("판정된 날은 판정 결과를 함께 준다")
	void judgedDayShowsResult() {
		long alice = rows.newUser("s10-judged");
		TestStudy study = rows.standardStudy(alice);
		rows.markJudged(study.studyId(), alice, NOV_19, "FAIL");
		setClock(seoul(NOV_20, 12, 0));

		JsonNode body = ok(day(alice, study.studyId(), NOV_19));

		assertThat(member(body, alice).get("result").asString()).isEqualTo("FAIL");
		assertThat(status(body, alice)).isEqualTo("REMAINING");
	}

	@Test
	@DisplayName("1기 시작 전처럼 기수 밖인 날은 아무도 판정하지 않는다 (R9, OD-07)")
	void dayOutsideSeasonIsNotJudged() {
		long alice = rows.newUser("s10-outside");
		TestStudy study = rows.standardStudy(alice);
		setClock(seoul(NOV_20, 12, 0));

		JsonNode body = ok(day(alice, study.studyId(), OCT_31));

		assertThat(body.get("inSeason").asBoolean()).isFalse();
		assertThat(status(body, alice)).isEqualTo("NOT_JUDGED");
		assertThat(body.get("summary").get("remaining").asInt()).isZero();
	}

	// --- 최근 풀이 ---

	@Test
	@DisplayName("최근 풀이: 그날 센 풀이를 수신 시각이 늦은 것부터. 출처(수동 인증 표시 자리)·제목·수신 시각을 준다")
	void recentSolvesShowSourceTitleAndTime() {
		long alice = rows.newUser("s10-recent-a");
		long bob = rows.newUser("s10-recent-b");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), bob, NOV_1);
		long bobSolve = rows.solve(bob, "BOJ", "2000", seoul(NOV_20, 11, 0));
		assign(bobSolve, bob, "2000", seoul(NOV_20, 11, 0));
		long aliceSolve = rows.githubSolve(alice, "BOJ", "1000", "A+B", seoul(NOV_20, 10, 0));
		assign(aliceSolve, alice, "1000", seoul(NOV_20, 10, 0));
		setClock(seoul(NOV_20, 12, 0));

		JsonNode recent = ok(today(alice, study.studyId())).get("recentSolves");

		assertThat(recent.size()).isEqualTo(2);
		assertThat(recent.get(0).get("userId").asLong()).isEqualTo(bob);
		assertThat(recent.get(0).get("source").asString()).isEqualTo("SELF_REPORT");
		assertThat(recent.get(0).get("platform").asString()).isEqualTo("BOJ");
		assertThat(recent.get(0).get("problemNo").asString()).isEqualTo("2000");
		assertThat(recent.get(0).get("receivedAt").asString()).isEqualTo("2026-11-20T02:00:00Z");
		assertThat(recent.get(1).get("userId").asLong()).isEqualTo(alice);
		assertThat(recent.get(1).get("source").asString()).isEqualTo("GITHUB");
		assertThat(recent.get(1).get("title").asString()).isEqualTo("A+B");
	}

	@Test
	@DisplayName("최근 풀이는 50개까지만 준다 (큰 응답을 만들지 않음, 9.4). 센 수는 모두 센다")
	void recentSolvesAreCapped() {
		long alice = rows.newUser("s10-many");
		TestStudy study = rows.standardStudy(alice);
		for (int i = 0; i < 51; i++) {
			solveAndAssign(alice, Integer.toString(1000 + i), seoul(NOV_20, 10, 0).plusSeconds(i));
		}
		setClock(seoul(NOV_20, 12, 0));

		JsonNode body = ok(today(alice, study.studyId()));

		assertThat(body.get("recentSolves").size()).isEqualTo(50);
		assertThat(member(body, alice).get("solvedCount").asInt()).isEqualTo(51);
	}

	// --- 요청 검증 ---

	@Test
	@DisplayName("아직 오지 않은 날은 400")
	void futureDayIsBadRequest() {
		long alice = rows.newUser("s10-future");
		TestStudy study = rows.standardStudy(alice);
		setClock(seoul(NOV_20, 12, 0));

		assertThat(day(alice, study.studyId(), NOV_21)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(day(alice, study.studyId(), NOV_20)).hasStatus(HttpStatus.OK);
	}

	@Test
	@DisplayName("날짜 형식이 틀리면 400")
	void malformedDateIsBadRequest() {
		long alice = rows.newUser("s10-bad-date");
		TestStudy study = rows.standardStudy(alice);
		setClock(seoul(NOV_20, 12, 0));

		assertThat(api.get(alice, "/api/studies/{studyId}/days/{date}", study.studyId(), "2026-13-01"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	// --- T19 ---

	@Test
	@DisplayName("T19: 다른 스터디의 멤버는 오늘 현황·특정 날 모두 404")
	void t19OtherStudyMemberGetsNotFound() {
		long alice = rows.newUser("s10-t19-a");
		long eve = rows.newUser("s10-t19-e");
		TestStudy study = rows.standardStudy(alice);
		rows.standardStudy(eve);
		setClock(seoul(NOV_20, 12, 0));

		assertThat(today(eve, study.studyId())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(day(eve, study.studyId(), NOV_20)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("T19: 탈퇴한 멤버와 없는 스터디는 404, 로그인하지 않으면 401")
	void t19LeftMemberAndMissingStudy() {
		long alice = rows.newUser("s10-t19-left");
		TestStudy study = rows.standardStudy(alice);
		rows.leave(study.membershipId(), seoul(NOV_19, 12, 0));
		setClock(seoul(NOV_20, 12, 0));

		assertThat(today(alice, study.studyId())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(day(alice, study.studyId(), NOV_19)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(today(alice, Long.MAX_VALUE)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(api.getAnonymous("/api/studies/{studyId}/today", study.studyId()))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@DisplayName("T19: 두 스터디에 속한 사용자가 있어도 현황에는 그 스터디의 멤버와 풀이만 나온다")
	void t19StatusIsScopedToTheStudy() {
		long alice = rows.newUser("s10-t19-both");
		long bob = rows.newUser("s10-t19-first");
		long carol = rows.newUser("s10-t19-second");
		TestStudy first = rows.standardStudy(alice);
		rows.join(first.studyId(), bob, NOV_1);
		TestStudy second = rows.standardStudy(carol);
		rows.join(second.studyId(), alice, NOV_1);
		solveAndAssign(alice, "1000", seoul(NOV_20, 10, 0));
		solveAndAssign(carol, "3000", seoul(NOV_20, 11, 0));
		setClock(seoul(NOV_20, 12, 0));

		JsonNode body = ok(today(bob, first.studyId()));

		assertThat(memberIds(body)).containsExactlyInAnyOrder(alice, bob);
		assertThat(body.get("recentSolves").size()).isEqualTo(1);
		assertThat(body.get("recentSolves").get(0).get("userId").asLong()).isEqualTo(alice);
	}

}
