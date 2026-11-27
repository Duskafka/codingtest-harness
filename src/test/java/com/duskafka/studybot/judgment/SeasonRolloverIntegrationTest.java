package com.duskafka.studybot.judgment;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.NOV_28;
import static com.duskafka.studybot.progress.StudyDayRows.SEOUL;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.judgment.JudgmentRows.Judged;
import com.duskafka.studybot.judgment.JudgmentRows.SeasonRow;
import com.duskafka.studybot.progress.SolveToAssign;
import com.duskafka.studybot.progress.StudyDayAssignment;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import com.duskafka.studybot.study.service.RuleSettings;
import com.duskafka.studybot.study.service.StudyRuleService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기수 자동 연장 (S12 만들 것 6, 수용 기준 OD-07·기수 시작 처리·새 기수 첫날·오래 꺼짐·예정 행의 끝 날짜, R13, 5.13).
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 기수 길이 28일, 1기 11-01~11-28. 다음 기수는 마지막 날 다음 날 바로 시작한다
 * (OD-07). 정산 중 → 종료는 정산 중이 된 시각 + 72시간이다 (사람이 정함, 2026-11-27 "실제 판정 시각 + 72시간").
 */
@IntegrationTest
class SeasonRolloverIntegrationTest {

	private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

	private static final LocalDate NOV_7 = LocalDate.of(2026, 11, 7);

	private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);

	private static final LocalDate NOV_24 = LocalDate.of(2026, 11, 24);

	private static final LocalDate NOV_25 = LocalDate.of(2026, 11, 25);

	private static final LocalDate NOV_27 = LocalDate.of(2026, 11, 27);

	private static final LocalDate NOV_29 = LocalDate.of(2026, 11, 29);

	private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);

	private static final LocalDate DEC_12 = LocalDate.of(2026, 12, 12);

	private static final LocalDate DEC_26 = LocalDate.of(2026, 12, 26);

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private DailyJudgmentBatch batch;

	@Autowired
	private StudyDayAssignment assignment;

	@Autowired
	private StudyRuleService ruleService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private StudyDayRows rows;

	private JudgmentRows judgments;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		judgments = new JudgmentRows(jdbc);
	}

	@Test
	@DisplayName("기수 시작일이 되면 배치가 그 기수를 ACTIVE로 바꾸고 다음 기수 행을 SCHEDULED로 만든다. 두 번 돌려도 기수 행은 하나다")
	void startDayActivatesSeasonAndSchedulesNext() {
		long alice = rows.newUser("s12-start");
		long studyId = scheduledStudy(alice);

		clock.setInstant(seoul(OCT_31, 23, 59));
		batch.judgeStudy(studyId);
		assertThat(judgments.seasons(studyId)).extracting(SeasonRow::seq, SeasonRow::status)
			.containsExactly(tuple(1, "SCHEDULED"));

		clock.setInstant(seoul(NOV_1, 0, 0));
		batch.judgeStudy(studyId);
		batch.judgeStudy(studyId);

		assertThat(judgments.seasons(studyId))
			.extracting(SeasonRow::seq, SeasonRow::startDay, SeasonRow::endDay, SeasonRow::status)
			.containsExactly(tuple(1, NOV_1, NOV_28, "ACTIVE"), tuple(2, NOV_29, DEC_26, "SCHEDULED"));
	}

	@Test
	@DisplayName("기수 마지막 날 판정 → 정산 중 → 72시간 뒤 종료. 다음 기수는 마지막 날 다음 날부터 진행되고 판정된다 (OD-07)")
	void lastDayJudgedThenSettlingThenClosed() {
		long alice = rows.newUser("s12-settle");
		TestStudy study = rows.standardStudy(alice);
		judgments.setLastJudgedDay(study.studyId(), NOV_27);

		Instant judgeLastDay = seoul(NOV_29, 0, 40);
		clock.setInstant(judgeLastDay.minusSeconds(1));
		batch.judgeStudy(study.studyId());
		assertThat(judgments.seasons(study.studyId())).extracting(SeasonRow::seq, SeasonRow::status)
			.as("새 기수 첫날이 시작했지만 마지막 날 판정 전이라 진행 기수가 둘")
			.containsExactly(tuple(1, "ACTIVE"), tuple(2, "ACTIVE"), tuple(3, "SCHEDULED"));

		clock.setInstant(judgeLastDay);
		batch.judgeStudy(study.studyId());
		SeasonRow settling = judgments.seasons(study.studyId()).getFirst();
		assertThat(settling.status()).isEqualTo("SETTLING");
		assertThat(settling.settlingStartedAt()).isEqualTo(judgeLastDay);
		assertThat(judgments.result(study.studyId(), alice, NOV_28)).isEqualTo("FAIL");

		clock.setInstant(judgeLastDay.plus(Duration.ofHours(72)).minusSeconds(1));
		batch.judgeStudy(study.studyId());
		assertThat(judgments.seasons(study.studyId()).getFirst().status()).isEqualTo("SETTLING");
		Judged firstDayOfNext = judgments.judged(study.studyId(), alice, NOV_29).orElseThrow();
		assertThat(firstDayOfNext.result()).as("다음 기수는 정산을 기다리지 않는다").isEqualTo("FAIL");
		assertThat(firstDayOfNext.seasonId()).isEqualTo(judgments.seasons(study.studyId()).get(1).id());

		clock.setInstant(judgeLastDay.plus(Duration.ofHours(72)));
		batch.judgeStudy(study.studyId());
		assertThat(judgments.seasons(study.studyId())).extracting(SeasonRow::seq, SeasonRow::status)
			.containsExactly(tuple(1, "CLOSED"), tuple(2, "ACTIVE"), tuple(3, "SCHEDULED"));
	}

	@Test
	@DisplayName("새 기수 첫날이 시작된 직후, 이전 기수 마지막 날 판정 전에도 그날은 새 기수 행 안에 있다 (판정 대상, season_id)")
	void newSeasonFirstDayIsInsideNewSeasonBeforeLastDayIsJudged() {
		long alice = rows.newUser("s12-first-day");
		TestStudy study = rows.standardStudy(alice);
		rows.putMemberDay(study.studyId(), alice, NOV_28, 1, null, null);
		judgments.setLastJudgedDay(study.studyId(), NOV_27);
		clock.setInstant(seoul(NOV_28, 12, 0));
		batch.judgeStudy(study.studyId());
		long nextSeasonId = judgments.seasons(study.studyId()).get(1).id();

		Instant receivedAt = seoul(NOV_29, 0, 5);
		clock.setInstant(receivedAt);
		long solveId = rows.solve(alice, "BOJ", "1000", receivedAt);
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> assignment
			.assign(new SolveToAssign(solveId, alice, "BOJ", "1000", receivedAt)));

		assertThat(judgments.result(study.studyId(), alice, NOV_28)).as("이전 기수 마지막 날은 아직 판정 전").isNull();
		Judged firstDay = judgments.judged(study.studyId(), alice, NOV_29).orElseThrow();
		assertThat(firstDay.solvedCount()).isEqualTo(1);
		assertThat(firstDay.seasonId()).isEqualTo(nextSeasonId);
	}

	@Test
	@DisplayName("서버가 기수 길이보다 오래 꺼졌다 켜져도 빠진 기수 행을 모두 만들고, 따라잡기 판정이 그 날들을 기수 밖으로 건너뛰지 않는다")
	void longOutageCreatesAllSeasonsAndJudgesEveryDay() {
		long alice = rows.newUser("s12-outage");
		long studyId = rows.newStudy(SEOUL);
		rows.addRule(studyId, NOV_1, 1, LocalTime.MIDNIGHT, 30, "");
		judgments.setSeasonLength(studyId, 7);
		judgments.insertSeason(studyId, 1, NOV_1, NOV_7, "ACTIVE");
		rows.join(studyId, alice, NOV_1);
		clock.setInstant(seoul(NOV_25, 0, 40));

		batch.judgeStudy(studyId);

		List<SeasonRow> seasons = judgments.seasons(studyId);
		assertThat(seasons).extracting(SeasonRow::seq, SeasonRow::startDay, SeasonRow::endDay, SeasonRow::status)
			.containsExactly(tuple(1, NOV_1, NOV_7, "SETTLING"),
					tuple(2, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14), "SETTLING"),
					tuple(3, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 21), "SETTLING"),
					tuple(4, LocalDate.of(2026, 11, 22), NOV_28, "ACTIVE"), tuple(5, NOV_29, LocalDate.of(2026, 12, 5),
							"SCHEDULED"));
		List<Judged> days = judgments.judgedRows(studyId, alice);
		assertThat(days).hasSize(24);
		assertThat(days).extracting(Judged::studyDay).first().isEqualTo(NOV_1);
		assertThat(days).extracting(Judged::studyDay).last().isEqualTo(NOV_24);
		assertThat(days).extracting(Judged::result).containsOnly("FAIL");
		for (Judged day : days) {
			SeasonRow season = seasons.stream()
				.filter(row -> !day.studyDay().isBefore(row.startDay()) && !day.studyDay().isAfter(row.endDay()))
				.findFirst()
				.orElseThrow();
			assertThat(day.seasonId()).as("%s의 기수", day.studyDay()).isEqualTo(season.id());
		}
		assertThat(judgments.lastJudgedDay(studyId)).isEqualTo(NOV_24);
	}

	@Test
	@DisplayName("기수 중에 기수 길이를 바꾸거나 그 변경을 취소하면 다음 기수 행의 끝 날짜가 그 길이로 바뀌고, 진행 중인 기수의 끝 날짜는 그대로다")
	void seasonLengthChangeReschedulesOnlyNextSeason() {
		long owner = rows.newUser("s12-length-owner");
		long studyId = rows.newStudy(SEOUL);
		rows.addRule(studyId, NOV_1, 1, LocalTime.MIDNIGHT, 30, "");
		judgments.insertSeason(studyId, 1, NOV_1, NOV_28, "ACTIVE");
		rows.joinAsOwner(studyId, owner, NOV_1);
		clock.setInstant(seoul(NOV_10, 12, 0));
		batch.judgeStudy(studyId);
		assertThat(judgments.seasons(studyId)).extracting(SeasonRow::endDay).containsExactly(NOV_28, DEC_26);

		ruleService.change(studyId, owner, lastRuleId(studyId), withSeasonLength(14));
		assertThat(judgments.seasons(studyId)).extracting(SeasonRow::seq, SeasonRow::endDay)
			.containsExactly(tuple(1, NOV_28), tuple(2, DEC_12));

		ruleService.change(studyId, owner, lastRuleId(studyId), withSeasonLength(28));
		assertThat(judgments.seasons(studyId)).extracting(SeasonRow::seq, SeasonRow::endDay)
			.as("변경을 취소하면 원래 길이로")
			.containsExactly(tuple(1, NOV_28), tuple(2, DEC_26));
	}

	@Test
	@DisplayName("기수 마지막 날에 기수 길이를 바꿔도 다음 기수 행의 끝 날짜가 그 길이로 바뀐다")
	void seasonLengthChangeOnLastDayReschedulesNextSeason() {
		long owner = rows.newUser("s12-length-last");
		long studyId = rows.newStudy(SEOUL);
		rows.addRule(studyId, NOV_1, 1, LocalTime.MIDNIGHT, 30, "");
		judgments.insertSeason(studyId, 1, NOV_1, NOV_28, "ACTIVE");
		rows.joinAsOwner(studyId, owner, NOV_1);
		clock.setInstant(seoul(NOV_28, 12, 0));
		batch.judgeStudy(studyId);

		ruleService.change(studyId, owner, lastRuleId(studyId), withSeasonLength(14));

		assertThat(judgments.seasons(studyId)).extracting(SeasonRow::seq, SeasonRow::endDay)
			.containsExactly(tuple(1, NOV_28), tuple(2, DEC_12));
	}

	// --- 도우미 ---

	/** 1기 11-01~11-28이 아직 SCHEDULED인 스터디. 사용자를 11-01부터 판정하는 멤버로 넣는다. */
	private long scheduledStudy(long userId) {
		long studyId = rows.newStudy(SEOUL);
		rows.addRule(studyId, NOV_1, 1, LocalTime.MIDNIGHT, 30, "");
		judgments.insertSeason(studyId, 1, NOV_1, NOV_28, "SCHEDULED");
		rows.join(studyId, userId, NOV_1);
		return studyId;
	}

	private long lastRuleId(long studyId) {
		return jdbc.sql("""
				select id from studybot.study_rules where study_id = :studyId order by effective_from desc limit 1
				""").param("studyId", studyId).query(Long.class).single();
	}

	private static RuleSettings withSeasonLength(int days) {
		RuleSettings base = RuleSettings.DEFAULTS;
		return new RuleSettings(base.dailyGoal(), base.dayBoundary(), base.graceMinutes(), base.restDays(),
				base.penaltyType(), base.fineAmount(), base.exemptionsPerSeason(), base.reminderOffsetMinutes(),
				base.selfReportMode(), days);
	}

}
