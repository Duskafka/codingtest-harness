package com.duskafka.studybot.judgment;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.SEOUL;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.judgment.JudgmentRows.Judged;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/**
 * 판정 배치 (S12 만들 것 2~5, 수용 기준 T9·T10·T11·T13·면제·가입·탈퇴·오류 격리·쿼리 수, 5.10, R9).
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 1기 11-01~11-28 ({@link StudyDayRows#standardStudy}).
 * 날 D의 판정 시각은 D+1 00:40이다 (기준 시각 + 유예 30분 + 여유 10분, R9). 2026-11-20은 금요일이다.
 *
 * <p>배치는 테스트가 {@link DailyJudgmentBatch#judgeStudy}로 스터디 하나씩 부른다. 모든 스터디를 도는 {@link DailyJudgmentBatch#run}은
 * 다른 테스트가 남긴 스터디(2026년)에 걸리지 않게 2025년 날짜로 본다.
 */
@IntegrationTest
class DailyJudgmentIntegrationTest {

	private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

	private static final LocalDate NOV_2 = LocalDate.of(2026, 11, 2);

	private static final LocalDate NOV_16 = LocalDate.of(2026, 11, 16);

	private static final LocalDate NOV_17 = LocalDate.of(2026, 11, 17);

	private static final LocalDate NOV_18 = LocalDate.of(2026, 11, 18);

	private static final LocalDate NOV_19 = LocalDate.of(2026, 11, 19);

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private static final LocalDate NOV_22 = LocalDate.of(2026, 11, 22);

	private static final LocalDate NOV_27 = LocalDate.of(2026, 11, 27);

	private static final LocalDate NOV_28 = LocalDate.of(2026, 11, 28);

	private static final LocalDate NOV_29 = LocalDate.of(2026, 11, 29);

	private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);

	/** 11-20의 판정 시각. */
	private static final Instant JUDGE_NOV_20 = seoul(NOV_21, 0, 40);

	/** 멤버 한 사람을 판정할 때마다 늘어나는 문장 수: 행 만들기, 잠그기, 저장 (사람이 정함 2026-11-27 "조회만 일정, 멤버별 잠금"). */
	private static final int STATEMENTS_PER_MEMBER = 3;

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private DailyJudgmentBatch batch;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private StudyDayRows rows;

	private JudgmentRows judgments;

	private TestApi api;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		judgments = new JudgmentRows(jdbc);
		api = new TestApi(mvc, jdbc);
	}

	@AfterEach
	void disableStatistics() {
		Statistics statistics = statistics();
		statistics.setStatisticsEnabled(false);
		statistics.clear();
	}

	@Test
	@DisplayName("판정 시각이 지난 날을 목표 달성이면 성공, 아니면 실패로 판정하고 판정에 쓴 규칙·시각·기수를 남긴다")
	void judgesSuccessAndFailWithRuleAndSeason() {
		long alice = rows.newUser("s12-basic-a");
		long bob = rows.newUser("s12-basic-b");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), bob, NOV_1);
		rows.putMemberDay(study.studyId(), alice, NOV_20, 1, null, null);
		judgments.setLastJudgedDay(study.studyId(), NOV_19);
		clock.setInstant(JUDGE_NOV_20);

		JudgmentSummary summary = batch.judgeStudy(study.studyId());

		Judged aliceDay = judgments.judged(study.studyId(), alice, NOV_20).orElseThrow();
		assertThat(aliceDay.result()).isEqualTo("SUCCESS");
		assertThat(aliceDay.resultSource()).isEqualTo("BATCH");
		assertThat(aliceDay.judgedAt()).isEqualTo(JUDGE_NOV_20);
		assertThat(aliceDay.ruleId()).isEqualTo(firstRuleId(study.studyId()));
		assertThat(aliceDay.seasonId()).as("판정이 그날의 기수 행을 채운다").isEqualTo(study.seasonId());
		Judged bobDay = judgments.judged(study.studyId(), bob, NOV_20).orElseThrow();
		assertThat(bobDay.result()).as("풀이가 없어 행이 없던 멤버도 판정한다").isEqualTo("FAIL");
		assertThat(bobDay.seasonId()).isEqualTo(study.seasonId());
		assertThat(judgments.lastJudgedDay(study.studyId())).isEqualTo(NOV_20);
		assertThat(judgments.result(study.studyId(), alice, NOV_21)).as("11-21은 아직 판정 시각 전").isNull();
		assertThat(summary.judgedDays()).isEqualTo(1);
		assertThat(summary.errors()).isZero();
	}

	@Test
	@DisplayName("판정 시각(다음 날 시작 + 유예 + 10분) 전에는 그날을 판정하지 않는다")
	void doesNotJudgeBeforeJudgeAt() {
		long alice = rows.newUser("s12-early");
		TestStudy study = rows.standardStudy(alice);
		judgments.setLastJudgedDay(study.studyId(), NOV_19);

		clock.setInstant(JUDGE_NOV_20.minusSeconds(1));
		batch.judgeStudy(study.studyId());
		assertThat(judgments.judged(study.studyId(), alice, NOV_20)).isEmpty();
		assertThat(judgments.lastJudgedDay(study.studyId())).isEqualTo(NOV_19);

		clock.setInstant(JUDGE_NOV_20);
		batch.judgeStudy(study.studyId());
		assertThat(judgments.result(study.studyId(), alice, NOV_20)).isEqualTo("FAIL");
	}

	@Test
	@DisplayName("T9: 판정 배치를 두 번 실행해도 결과가 그대로다. 이미 결과가 있는 멤버 일은 건너뛴다")
	void t9RunningTwiceKeepsResults() {
		long alice = rows.newUser("s12-t9-a");
		long bob = rows.newUser("s12-t9-b");
		long carol = rows.newUser("s12-t9-c");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), bob, NOV_1);
		rows.join(study.studyId(), carol, NOV_1);
		rows.putMemberDay(study.studyId(), alice, NOV_20, 1, null, null);
		rows.markJudged(study.studyId(), carol, NOV_20, "SUCCESS");
		judgments.setLastJudgedDay(study.studyId(), NOV_19);
		clock.setInstant(JUDGE_NOV_20);

		batch.judgeStudy(study.studyId());
		List<Judged> first = List.of(judged(study, alice, NOV_20), judged(study, bob, NOV_20),
				judged(study, carol, NOV_20));
		clock.advance(Duration.ofMinutes(5));
		JudgmentSummary second = batch.judgeStudy(study.studyId());

		assertThat(List.of(judged(study, alice, NOV_20), judged(study, bob, NOV_20), judged(study, carol, NOV_20)))
			.isEqualTo(first);
		assertThat(first).extracting(Judged::result).containsExactly("SUCCESS", "FAIL", "SUCCESS");
		assertThat(first.get(2).resultSource()).as("이미 결과가 있던 멤버 일은 판정이 고치지 않는다").isNull();
		assertThat(second.judgedDays()).isZero();
		assertThat(judgments.lastJudgedDay(study.studyId())).isEqualTo(NOV_20);
	}

	@Test
	@DisplayName("T10: 서버가 사흘 꺼졌다 켜지면 사흘치를 오래된 순서로 판정한다")
	void t10CatchesUpThreeDaysInOrder() {
		long alice = rows.newUser("s12-t10-a");
		long bob = rows.newUser("s12-t10-b");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), bob, NOV_1);
		rows.putMemberDay(study.studyId(), alice, NOV_17, 1, null, null);
		rows.putMemberDay(study.studyId(), alice, NOV_19, 1, null, null);
		judgments.setLastJudgedDay(study.studyId(), NOV_16);
		clock.setInstant(seoul(NOV_20, 0, 40));

		JudgmentSummary summary = batch.judgeStudy(study.studyId());

		assertThat(judgments.judgedRows(study.studyId(), alice)).extracting(Judged::studyDay, Judged::result)
			.containsExactly(tuple(NOV_17, "SUCCESS"), tuple(NOV_18, "FAIL"), tuple(NOV_19, "SUCCESS"));
		List<Judged> bobDays = judgments.judgedRows(study.studyId(), bob);
		assertThat(bobDays).extracting(Judged::studyDay).containsExactly(NOV_17, NOV_18, NOV_19);
		assertThat(bobDays).extracting(Judged::result).containsOnly("FAIL");
		assertThat(bobDays).extracting(Judged::id).as("판정이 만든 행의 순서 = 판정한 순서").isSorted();
		assertThat(judgments.lastJudgedDay(study.studyId())).isEqualTo(NOV_19);
		assertThat(summary.judgedDays()).isEqualTo(3);
	}

	@Test
	@DisplayName("T11: 규칙을 바꾼 날까지는 이전 규칙(목표)으로, 다음 날부터는 새 규칙으로 판정한다")
	void t11JudgesEachDayWithItsRuleVersion() {
		long alice = rows.newUser("s12-t11-a");
		TestStudy study = rows.standardStudy(alice);
		long goalTwo = rows.addRule(study.studyId(), NOV_20, 2, LocalTime.MIDNIGHT, 30, "");
		rows.putMemberDay(study.studyId(), alice, NOV_19, 1, null, null);
		rows.putMemberDay(study.studyId(), alice, NOV_20, 1, null, null);
		judgments.setLastJudgedDay(study.studyId(), NOV_18);
		clock.setInstant(JUDGE_NOV_20);

		batch.judgeStudy(study.studyId());

		Judged nov19 = judged(study, alice, NOV_19);
		assertThat(nov19.result()).as("목표 1인 옛 규칙").isEqualTo("SUCCESS");
		assertThat(nov19.ruleId()).isEqualTo(firstRuleId(study.studyId()));
		Judged nov20 = judged(study, alice, NOV_20);
		assertThat(nov20.result()).as("목표 2인 새 규칙").isEqualTo("FAIL");
		assertThat(nov20.ruleId()).isEqualTo(goalTwo);
	}

	@Test
	@DisplayName("T11: 다음 기수부터 적용하는 기준 시각은 다음 기수 첫날부터 새 규칙으로 판정한다 (판정 시각도 새 규칙)")
	void t11NextSeasonItemsApplyFromNextSeason() {
		long alice = rows.newUser("s12-t11-season");
		TestStudy study = rows.standardStudy(alice);
		long sixAm = rows.addRule(study.studyId(), NOV_29, 1, LocalTime.of(6, 0), 30, "");
		rows.putMemberDay(study.studyId(), alice, NOV_29, 1, null, null);
		judgments.setLastJudgedDay(study.studyId(), NOV_27);

		clock.setInstant(seoul(NOV_30, 0, 41));
		batch.judgeStudy(study.studyId());
		Judged lastDay = judged(study, alice, NOV_28);
		assertThat(lastDay.result()).isEqualTo("FAIL");
		assertThat(lastDay.ruleId()).as("지난 기수 마지막 날은 옛 규칙").isEqualTo(firstRuleId(study.studyId()));
		assertThat(judgments.result(study.studyId(), alice, NOV_29))
			.as("새 기수 첫날은 새 기준 시각 06:00에 끝나서 아직 판정 시각 전").isNull();

		clock.setInstant(seoul(NOV_30, 6, 40));
		batch.judgeStudy(study.studyId());
		Judged firstDay = judged(study, alice, NOV_29);
		assertThat(firstDay.result()).isEqualTo("SUCCESS");
		assertThat(firstDay.ruleId()).isEqualTo(sixAm);
		assertThat(firstDay.seasonId()).isEqualTo(judgments.seasons(study.studyId()).get(1).id());
	}

	@Test
	@DisplayName("T13: 면제권을 쓴 날 목표를 달성하면 성공이고 면제권이 돌아온다")
	void t13ExemptionDaySuccessReturnsTicket() {
		long alice = rows.newUser("s12-t13");
		TestStudy study = rows.standardStudy(alice);
		rows.putMemberDay(study.studyId(), alice, NOV_20, 1, null, seoul(NOV_20, 9, 0));
		judgments.setLastJudgedDay(study.studyId(), NOV_19);
		clock.setInstant(JUDGE_NOV_20.minusSeconds(1));
		assertThat(exemptionsRemaining(alice, study.studyId())).as("판정 전에는 쓴 것으로 셈").isEqualTo(3);

		clock.setInstant(JUDGE_NOV_20);
		batch.judgeStudy(study.studyId());

		assertThat(judgments.result(study.studyId(), alice, NOV_20)).isEqualTo("SUCCESS");
		assertThat(exemptionsRemaining(alice, study.studyId())).isEqualTo(4);
	}

	@Test
	@DisplayName("면제권을 쓰고 못 푼 날은 EXEMPT_TICKET, 쉬는 날은 풀었어도 EXEMPT_REST")
	void exemptionAndRestDay() {
		long alice = rows.newUser("s12-exempt-a");
		long bob = rows.newUser("s12-exempt-b");
		TestStudy study = rows.standardStudy(alice, SEOUL, LocalTime.MIDNIGHT, 30, "FRIDAY");
		rows.join(study.studyId(), bob, NOV_1);
		rows.putMemberDay(study.studyId(), alice, NOV_19, 0, null, seoul(NOV_19, 9, 0));
		rows.putMemberDay(study.studyId(), bob, NOV_20, 1, null, null);
		judgments.setLastJudgedDay(study.studyId(), NOV_18);
		clock.setInstant(JUDGE_NOV_20);

		batch.judgeStudy(study.studyId());

		assertThat(judgments.result(study.studyId(), alice, NOV_19)).isEqualTo("EXEMPT_TICKET");
		assertThat(judgments.result(study.studyId(), bob, NOV_19)).isEqualTo("FAIL");
		assertThat(judgments.result(study.studyId(), alice, NOV_20)).as("금요일은 쉬는 날").isEqualTo("EXEMPT_REST");
		assertThat(judgments.result(study.studyId(), bob, NOV_20)).isEqualTo("EXEMPT_REST");
	}

	@Test
	@DisplayName("가입한 날은 판정하지 않고 탈퇴한 날까지는 판정한다. 같은 날 탈퇴하고 다시 가입해도 그날은 한 번 판정한다")
	void joinLeaveAndRejoin() {
		long alice = rows.newUser("s12-member-a");
		long joiner = rows.newUser("s12-member-join");
		long leaver = rows.newUser("s12-member-leave");
		long rejoiner = rows.newUser("s12-member-rejoin");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), joiner, seoul(NOV_20, 9, 0), NOV_21);
		long leaving = rows.join(study.studyId(), leaver, NOV_1);
		rows.leave(leaving, seoul(NOV_20, 10, 0));
		long first = rows.join(study.studyId(), rejoiner, NOV_1);
		rows.leave(first, seoul(NOV_20, 10, 0));
		rows.join(study.studyId(), rejoiner, seoul(NOV_20, 11, 0), NOV_21);
		judgments.setLastJudgedDay(study.studyId(), NOV_19);
		clock.setInstant(seoul(NOV_22, 0, 40));

		batch.judgeStudy(study.studyId());

		assertThat(judgments.judged(study.studyId(), joiner, NOV_20)).as("가입한 날").isEmpty();
		assertThat(judgments.result(study.studyId(), joiner, NOV_21)).isEqualTo("FAIL");
		assertThat(judgments.result(study.studyId(), leaver, NOV_20)).as("탈퇴한 날").isEqualTo("FAIL");
		assertThat(judgments.judged(study.studyId(), leaver, NOV_21)).as("탈퇴한 다음 날").isEmpty();
		assertThat(judgments.judgedRows(study.studyId(), rejoiner)).extracting(Judged::studyDay, Judged::result)
			.containsExactly(tuple(NOV_20, "FAIL"), tuple(NOV_21, "FAIL"));
		assertThat(judgments.lastJudgedDay(study.studyId())).isEqualTo(NOV_21);
	}

	@Test
	@DisplayName("1기 시작 전 날은 판정하지 않는다. 진행 지점이 비어 있으면 1기 시작일부터 판정한다")
	void startsFromFirstSeasonDay() {
		long alice = rows.newUser("s12-first");
		TestStudy study = rows.standardStudy(alice);
		rows.putMemberDay(study.studyId(), alice, OCT_31, 1, null, null);
		clock.setInstant(seoul(NOV_2, 0, 40));

		batch.judgeStudy(study.studyId());

		assertThat(judgments.result(study.studyId(), alice, OCT_31)).as("기수 밖").isNull();
		assertThat(judgments.result(study.studyId(), alice, NOV_1)).isEqualTo("FAIL");
		assertThat(judgments.lastJudgedDay(study.studyId())).isEqualTo(NOV_1);
	}

	@Test
	@DisplayName("한 멤버의 판정에서 오류가 나도 다른 멤버는 판정되고, 그날의 last_judged_day는 올라가지 않는다")
	void oneMemberErrorDoesNotBlockOthers() {
		long alice = rows.newUser("s12-error-a");
		long broken = rows.newUser("s12-error-x");
		long carol = rows.newUser("s12-error-c");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), broken, NOV_1);
		rows.join(study.studyId(), carol, NOV_1);
		rows.putMemberDay(study.studyId(), carol, NOV_20, 1, null, null);
		judgments.setLastJudgedDay(study.studyId(), NOV_19);
		clock.setInstant(seoul(NOV_22, 0, 40));
		String trigger = "s12_fail_judgment_" + broken;
		jdbc.sql("""
				create function studybot.%1$s() returns trigger language plpgsql as $f$
				begin
					if new.user_id = %2$d and new.result is not null then
						raise exception 'S12 테스트가 일부러 낸 오류';
					end if;
					return new;
				end $f$
				""".formatted(trigger, broken)).update();
		jdbc.sql("create trigger %1$s before update on studybot.member_days for each row execute function studybot.%1$s()"
			.formatted(trigger)).update();
		JudgmentSummary withError;
		try {
			withError = batch.judgeStudy(study.studyId());
		}
		finally {
			jdbc.sql("drop trigger %1$s on studybot.member_days".formatted(trigger)).update();
			jdbc.sql("drop function studybot.%1$s()".formatted(trigger)).update();
		}

		assertThat(judgments.result(study.studyId(), alice, NOV_20)).isEqualTo("FAIL");
		assertThat(judgments.result(study.studyId(), carol, NOV_20)).isEqualTo("SUCCESS");
		assertThat(judgments.result(study.studyId(), broken, NOV_20)).isNull();
		assertThat(judgments.lastJudgedDay(study.studyId())).isEqualTo(NOV_19);
		assertThat(judgments.judged(study.studyId(), alice, NOV_21)).as("오류가 난 날 뒤로 건너뛰지 않는다").isEmpty();
		assertThat(withError.errors()).isEqualTo(1);

		batch.judgeStudy(study.studyId());

		assertThat(judgments.result(study.studyId(), broken, NOV_20)).isEqualTo("FAIL");
		assertThat(judgments.result(study.studyId(), alice, NOV_21)).isEqualTo("FAIL");
		assertThat(judgments.lastJudgedDay(study.studyId())).isEqualTo(NOV_21);
	}

	@Test
	@DisplayName("판정 배치의 조회 쿼리 수는 멤버 수에 비례해 늘지 않는다. 멤버마다는 잠금·저장 문장만 늘어난다")
	void lookupQueriesDoNotGrowWithMembers() {
		long[] small = studyWithMembers(3, "s12-q3");
		long[] large = studyWithMembers(30, "s12-q30");
		clock.setInstant(JUDGE_NOV_20);

		long forThree = statementsToJudge(small[1]);
		long forThirty = statementsToJudge(large[1]);

		assertThat(judgments.result(large[1], large[0], NOV_20)).isEqualTo("FAIL");
		assertThat(forThree - 3L * STATEMENTS_PER_MEMBER).as("멤버와 상관없는 문장 수").isPositive();
		assertThat(forThirty - 30L * STATEMENTS_PER_MEMBER)
			.as("멤버 30명일 때 멤버와 상관없는 문장 수 (전체 %d, 3명일 때 전체 %d)", forThirty, forThree)
			.isEqualTo(forThree - 3L * STATEMENTS_PER_MEMBER);
	}

	@Test
	@DisplayName("배치 한 번은 모든 활성 스터디를 판정하고, 한 스터디의 오류가 다른 스터디를 막지 않는다")
	void runJudgesEveryStudyAndIsolatesStudies() {
		LocalDate mar1 = LocalDate.of(2025, 3, 1);
		LocalDate mar2 = LocalDate.of(2025, 3, 2);
		long brokenStudy = rows.newStudy(SEOUL);
		judgments.insertSeason(brokenStudy, 1, mar1, LocalDate.of(2025, 3, 28), "ACTIVE");
		long alice = rows.newUser("s12-run");
		long studyId = rows.newStudy(SEOUL);
		rows.addRule(studyId, mar1, 1, LocalTime.MIDNIGHT, 30, "");
		judgments.insertSeason(studyId, 1, mar1, LocalDate.of(2025, 3, 28), "ACTIVE");
		rows.join(studyId, alice, seoul(LocalDate.of(2025, 2, 1), 9, 0), mar1);
		clock.setInstant(seoul(LocalDate.of(2025, 3, 3), 0, 40));

		JudgmentSummary summary = batch.run();

		assertThat(judgments.judgedRows(studyId, alice)).extracting(Judged::studyDay, Judged::result)
			.containsExactly(tuple(mar1, "FAIL"), tuple(mar2, "FAIL"));
		assertThat(judgments.lastJudgedDay(studyId)).isEqualTo(mar2);
		assertThat(judgments.lastJudgedDay(brokenStudy)).as("규칙 버전이 없는 스터디").isNull();
		assertThat(summary.judgedDays()).isGreaterThanOrEqualTo(2);
		assertThat(summary.errors()).isPositive();
	}

	// --- 도우미 ---

	private Judged judged(TestStudy study, long userId, LocalDate day) {
		return judgments.judged(study.studyId(), userId, day).orElseThrow();
	}

	private long firstRuleId(long studyId) {
		return jdbc.sql("select id from studybot.study_rules where study_id = :studyId order by effective_from limit 1")
			.param("studyId", studyId)
			.query(Long.class)
			.single();
	}

	private int exemptionsRemaining(long userId, long studyId) {
		MvcTestResult result = api.get(userId, "/api/studies/{studyId}/today", studyId);
		assertThat(result).hasStatus(HttpStatus.OK);
		for (JsonNode member : TestApi.json(result).get("members")) {
			if (member.get("userId").asLong() == userId) {
				return member.get("exemptionsRemaining").asInt();
			}
		}
		throw new AssertionError("현황에 멤버가 없어요: " + userId);
	}

	/**
	 * 멤버가 {@code memberCount}명이고 11-20을 판정할 차례인 스터디. 멤버마다 다른 경로(풀이 있음·면제권·행 없음)가 나오게 한다.
	 *
	 * @return 첫 멤버의 ID와 스터디 ID
	 */
	private long[] studyWithMembers(int memberCount, String prefix) {
		long first = rows.newUser(prefix + "-0");
		TestStudy study = rows.standardStudy(first);
		for (int i = 1; i < memberCount; i++) {
			long member = rows.newUser(prefix + "-" + i);
			rows.join(study.studyId(), member, NOV_1);
			if (i % 3 == 1) {
				rows.putMemberDay(study.studyId(), member, NOV_20, 1, null, null);
			}
			else if (i % 3 == 2) {
				rows.putMemberDay(study.studyId(), member, NOV_20, 0, null, seoul(NOV_20, 9, 0));
			}
		}
		judgments.setLastJudgedDay(study.studyId(), NOV_19);
		return new long[] { first, study.studyId() };
	}

	/** 스터디 하나를 판정하고 Hibernate가 준비한 문장 수를 돌려준다. */
	private long statementsToJudge(long studyId) {
		Statistics statistics = statistics();
		statistics.clear();
		statistics.setStatisticsEnabled(true);
		batch.judgeStudy(studyId);
		statistics.setStatisticsEnabled(false);
		return statistics.getPrepareStatementCount();
	}

	private Statistics statistics() {
		return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

}
