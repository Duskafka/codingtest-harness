package com.duskafka.studybot.progress;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.common.time.RuleVersionCalendar;
import com.duskafka.studybot.common.time.StudyTimeRule;
import com.duskafka.studybot.study.MemberStudy;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 배정 계획의 단위 테스트 (R4, 5.5, testing.md 8.1 '단위: 배정(R4)'). DB 없이 계획과 결정만 본다. 잠금과 동시성은
 * {@code StudyDayAssignmentIntegrationTest}가 본다.
 *
 * <p>S09 수용 기준의 기본값: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제. 2026-11-20은 금요일, 11-21은 토요일이다. 1기는 11-01~11-28.
 */
class AssignmentPlanTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

	private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private static final long SEASON_ID = 70L;

	private static final MemberStudy.SeasonPeriod SEASON_1 = new MemberStudy.SeasonPeriod(SEASON_ID, NOV_1,
			LocalDate.of(2026, 11, 28));

	private static final MemberDayState UNJUDGED_EMPTY = new MemberDayState(false, false, 0);

	/** 서울 시각 11-21 hh:mm (= 11-20 15:00Z + …). */
	private static Instant seoulNov21(int hour, int minute) {
		return LocalDate.of(2026, 11, 21).atTime(hour, minute).atZone(SEOUL).toInstant();
	}

	private static StudyTimeRule timeRule(ZoneId zone, LocalTime boundary, int grace, Set<DayOfWeek> restDays) {
		return new StudyTimeRule(zone, boundary, grace, 120, restDays);
	}

	/** 규칙 버전 하나(11-01부터, 목표 1)인 스터디. */
	private static MemberStudy study(long studyId, StudyTimeRule rule, LocalDate judgedFrom,
			List<MemberStudy.SeasonPeriod> seasons) {
		return new MemberStudy(studyId, judgedFrom,
				RuleVersionCalendar.of(List.of(new RuleVersionCalendar.Version(NOV_1, rule))),
				List.of(new MemberStudy.DailyGoal(NOV_1, 1)), seasons);
	}

	private static MemberStudy defaultStudy() {
		return study(1L, timeRule(SEOUL, LocalTime.MIDNIGHT, 30, Set.of()), NOV_1, List.of(SEASON_1));
	}

	@Nested
	@DisplayName("T6: 유예 안(00:05) 수신")
	class InGrace {

		@Test
		@DisplayName("T6: 00:05 수신이면 전날(11-20)이 후보이고, 전날 미달이면 전날로 배정")
		void t6PreviousDayBelowGoalTakesTheSolve() {
			AssignmentPlan plan = AssignmentPlan.of(defaultStudy(), seoulNov21(0, 5));

			assertThat(plan.today()).isEqualTo(NOV_21);
			assertThat(plan.previous()).isEqualTo(NOV_20);
			assertThat(plan.previousGoal()).isEqualTo(1);
			assertThat(plan.decide(UNJUDGED_EMPTY)).isEqualTo(NOV_20);
		}

		@Test
		@DisplayName("T6: 00:05 수신이어도 전날이 이미 목표를 달성했으면 오늘로 배정")
		void t6PreviousDayAtGoalSendsTheSolveToToday() {
			AssignmentPlan plan = AssignmentPlan.of(defaultStudy(), seoulNov21(0, 5));

			assertThat(plan.decide(new MemberDayState(false, false, 1))).isEqualTo(NOV_21);
		}

		@Test
		@DisplayName("S09: 전날이 이미 판정됐으면 유예 안이어도 오늘로 배정")
		void judgedPreviousDaySendsTheSolveToToday() {
			AssignmentPlan plan = AssignmentPlan.of(defaultStudy(), seoulNov21(0, 5));

			assertThat(plan.decide(new MemberDayState(true, false, 0))).isEqualTo(NOV_21);
		}

		@Test
		@DisplayName("S09: 전날에 면제권을 썼으면 오늘로 배정 (R4 '면제권 아님')")
		void exemptionOnPreviousDaySendsTheSolveToToday() {
			AssignmentPlan plan = AssignmentPlan.of(defaultStudy(), seoulNov21(0, 5));

			assertThat(plan.decide(new MemberDayState(false, true, 0))).isEqualTo(NOV_21);
		}

		@Test
		@DisplayName("S09: 전날(금요일)이 쉬는 요일이면 후보가 아니고 오늘로 배정")
		void restDayIsNotACandidate() {
			MemberStudy study = study(1L, timeRule(SEOUL, LocalTime.MIDNIGHT, 30, Set.of(DayOfWeek.FRIDAY)), NOV_1,
					List.of(SEASON_1));

			AssignmentPlan plan = AssignmentPlan.of(study, seoulNov21(0, 5));

			assertThat(plan.previous()).isNull();
			assertThat(plan.decide(null)).isEqualTo(NOV_21);
		}

		@Test
		@DisplayName("S09: 오늘(토요일)이 쉬는 요일이어도 전날이 후보면 전날로 배정 (쉬는 날은 전날 규칙으로만 본다)")
		void restDayTodayDoesNotMatter() {
			MemberStudy study = study(1L, timeRule(SEOUL, LocalTime.MIDNIGHT, 30, Set.of(DayOfWeek.SATURDAY)), NOV_1,
					List.of(SEASON_1));

			AssignmentPlan plan = AssignmentPlan.of(study, seoulNov21(0, 5));

			assertThat(plan.decide(UNJUDGED_EMPTY)).isEqualTo(NOV_20);
		}

	}

	@Nested
	@DisplayName("유예 경계")
	class GraceBoundary {

		@Test
		@DisplayName("S09: 유예 밖(00:35) 수신은 전날 미달이어도 오늘로 배정")
		void outsideGraceGoesToToday() {
			AssignmentPlan plan = AssignmentPlan.of(defaultStudy(), seoulNov21(0, 35));

			assertThat(plan.previous()).isNull();
			assertThat(plan.decide(null)).isEqualTo(NOV_21);
		}

		@Test
		@DisplayName("S09: 기준 시각 정각(00:00)은 유예 안, 유예 끝(00:30)은 유예 밖 (시작 포함, 끝 제외)")
		void graceIncludesStartAndExcludesEnd() {
			assertThat(AssignmentPlan.of(defaultStudy(), seoulNov21(0, 0)).previous()).isEqualTo(NOV_20);
			assertThat(AssignmentPlan.of(defaultStudy(), seoulNov21(0, 29)).previous()).isEqualTo(NOV_20);
			assertThat(AssignmentPlan.of(defaultStudy(), seoulNov21(0, 30)).previous()).isNull();
			assertThat(AssignmentPlan.of(defaultStudy(), seoulNov21(0, 0).minusNanos(1)).today()).isEqualTo(NOV_20);
		}

		@Test
		@DisplayName("S09: 유예가 0분이면 늘 오늘로 배정")
		void zeroGraceNeverTakesThePreviousDay() {
			MemberStudy study = study(1L, timeRule(SEOUL, LocalTime.MIDNIGHT, 0, Set.of()), NOV_1, List.of(SEASON_1));

			assertThat(AssignmentPlan.of(study, seoulNov21(0, 0)).previous()).isNull();
		}

	}

	@Nested
	@DisplayName("규칙 버전 (전날 목표는 전날 규칙, 유예는 N 규칙)")
	class RuleVersions {

		private MemberStudy twoVersions(int oldGoal, int oldGrace, int newGoal, int newGrace) {
			StudyTimeRule oldRule = timeRule(SEOUL, LocalTime.MIDNIGHT, oldGrace, Set.of());
			StudyTimeRule newRule = timeRule(SEOUL, LocalTime.MIDNIGHT, newGrace, Set.of());
			return new MemberStudy(1L, NOV_1,
					RuleVersionCalendar.of(List.of(new RuleVersionCalendar.Version(NOV_1, oldRule),
							new RuleVersionCalendar.Version(NOV_21, newRule))),
					List.of(new MemberStudy.DailyGoal(NOV_1, oldGoal), new MemberStudy.DailyGoal(NOV_21, newGoal)),
					List.of(SEASON_1));
		}

		@Test
		@DisplayName("S09: 전날 목표는 전날 규칙 버전(목표 2)으로 본다. 오늘 버전의 목표(1)로 보면 이미 달성이라 오늘로 갔을 것")
		void previousGoalComesFromThePreviousDaysVersion() {
			AssignmentPlan plan = AssignmentPlan.of(twoVersions(2, 30, 1, 30), seoulNov21(0, 5));

			assertThat(plan.previousGoal()).isEqualTo(2);
			assertThat(plan.decide(new MemberDayState(false, false, 1))).isEqualTo(NOV_20);
		}

		@Test
		@DisplayName("S09: 유예는 N(11-21)의 규칙이다. 전날 30분, N 60분이면 00:45는 유예 안 (R4 'N의 규칙의 유예')")
		void graceComesFromTodaysVersion() {
			assertThat(AssignmentPlan.of(twoVersions(1, 30, 1, 60), seoulNov21(0, 45)).previous()).isEqualTo(NOV_20);
			assertThat(AssignmentPlan.of(twoVersions(1, 60, 1, 30), seoulNov21(0, 45)).previous()).isNull();
		}

	}

	@Nested
	@DisplayName("판정 대상 (R9: 가입 다음 날부터, 기수 안)")
	class JudgeTarget {

		@Test
		@DisplayName("S09: 가입한 날(판정 대상 아님, R14)은 후보가 아니라 가입 다음 날 00:05의 풀이는 오늘로 배정")
		void joinDayIsNotACandidate() {
			MemberStudy study = study(1L, timeRule(SEOUL, LocalTime.MIDNIGHT, 30, Set.of()), NOV_21, List.of(SEASON_1));

			AssignmentPlan plan = AssignmentPlan.of(study, seoulNov21(0, 5));

			assertThat(plan.previous()).isNull();
			assertThat(plan.decide(null)).isEqualTo(NOV_21);
		}

		@Test
		@DisplayName("S09: 기수 밖인 전날(1기 시작 전날)은 후보가 아니다. 오늘은 기수 행 ID를, 기수 밖인 날은 빈 기수를 가진다")
		void dayOutsideSeasonsIsNotACandidate() {
			MemberStudy.SeasonPeriod fromNov21 = new MemberStudy.SeasonPeriod(SEASON_ID, NOV_21, LocalDate.of(2026, 12, 18));
			MemberStudy study = study(1L, timeRule(SEOUL, LocalTime.MIDNIGHT, 30, Set.of()), NOV_1, List.of(fromNov21));

			AssignmentPlan firstDay = AssignmentPlan.of(study, seoulNov21(0, 5));
			AssignmentPlan dayBefore = AssignmentPlan.of(study, seoulNov21(0, 5).minusSeconds(3600));

			assertThat(firstDay.previous()).isNull();
			assertThat(firstDay.todaySeasonId()).isEqualTo(SEASON_ID);
			assertThat(dayBefore.today()).isEqualTo(NOV_20);
			assertThat(dayBefore.todaySeasonId()).isNull();
		}

		@Test
		@DisplayName("S09: 판정 대상이 아닌 오늘(가입한 날)이어도 오늘로 배정한다 (부록 D recordSolve는 늘 기록, 판정만 하지 않음)")
		void nonJudgedTodayStillGetsTheSolve() {
			MemberStudy study = study(1L, timeRule(SEOUL, LocalTime.MIDNIGHT, 30, Set.of()), LocalDate.of(2026, 11, 22),
					List.of(SEASON_1));

			AssignmentPlan plan = AssignmentPlan.of(study, seoulNov21(15, 0));

			assertThat(plan.decide(null)).isEqualTo(NOV_21);
			assertThat(plan.rowsToLock()).containsExactly(new MemberDayKey(1L, NOV_21, SEASON_ID));
		}

	}

	@Nested
	@DisplayName("T25: 기준 시각과 시간대")
	class BoundaryAndZone {

		@Test
		@DisplayName("T25: 기준 06:00 스터디의 새벽 03:00 풀이는 전날(11-20)로 집계된다. 유예(06:00~06:30)와는 상관없다")
		void t25EarlyMorningCountsForThePreviousCalendarDate() {
			MemberStudy study = study(1L, timeRule(SEOUL, LocalTime.of(6, 0), 30, Set.of()), NOV_1, List.of(SEASON_1));

			AssignmentPlan night = AssignmentPlan.of(study, seoulNov21(3, 0));
			AssignmentPlan grace = AssignmentPlan.of(study, seoulNov21(6, 10));

			assertThat(night.today()).isEqualTo(NOV_20);
			assertThat(night.previous()).isNull();
			assertThat(grace.today()).isEqualTo(NOV_21);
			assertThat(grace.previous()).isEqualTo(NOV_20);
		}

		@Test
		@DisplayName("T25: 같은 시각도 스터디 시간대로 센다. 서울 14:05(유예 밖)는 오늘, 뉴욕 00:05(유예 안)는 전날이 후보")
		void t25EachStudyUsesItsOwnTimeZone() {
			Instant instant = Instant.parse("2026-11-21T05:05:00Z");
			MemberStudy seoul = defaultStudy();
			MemberStudy newYork = study(2L, timeRule(NEW_YORK, LocalTime.MIDNIGHT, 30, Set.of()), NOV_1, List.of(SEASON_1));

			assertThat(AssignmentPlan.of(seoul, instant).previous()).isNull();
			assertThat(AssignmentPlan.of(seoul, instant).today()).isEqualTo(NOV_21);
			assertThat(AssignmentPlan.of(newYork, instant).today()).isEqualTo(NOV_21);
			assertThat(AssignmentPlan.of(newYork, instant).previous()).isEqualTo(NOV_20);
		}

	}

	@Nested
	@DisplayName("잠글 행과 순서 (결정 제안서 007)")
	class LockRows {

		@Test
		@DisplayName("S09: 후보 전날이 있으면 전날과 오늘 둘 다 잠근다 (전날로 배정해도 오늘 행을 잠금). 기수 행 ID를 함께 넘긴다")
		void candidatePreviousAndTodayAreLocked() {
			AssignmentPlan plan = AssignmentPlan.of(defaultStudy(), seoulNov21(0, 5));

			assertThat(plan.rowsToLock()).containsExactly(new MemberDayKey(1L, NOV_20, SEASON_ID),
					new MemberDayKey(1L, NOV_21, SEASON_ID));
		}

		@Test
		@DisplayName("S09: 잠금 순서는 스터디 일이 이른 것부터, 같은 날이면 스터디 ID 순서. 겹치는 행은 한 번만")
		void lockOrderIsByDayThenStudy() {
			LocalDate nov19 = LocalDate.of(2026, 11, 19);
			MemberDayKey study5Prev = new MemberDayKey(5L, NOV_20, 1L);
			MemberDayKey study5Today = new MemberDayKey(5L, NOV_21, 1L);
			MemberDayKey study2Prev = new MemberDayKey(2L, NOV_20, 2L);
			MemberDayKey study2Today = new MemberDayKey(2L, NOV_21, 2L);
			MemberDayKey newYorkPrev = new MemberDayKey(9L, nov19, 3L);
			MemberDayKey newYorkToday = new MemberDayKey(9L, NOV_20, 3L);

			List<MemberDayKey> ordered = MemberDayKey.inLockOrder(List.of(study5Prev, study5Today, study2Prev,
					study2Today, newYorkPrev, newYorkToday, study5Prev, study2Today));

			assertThat(ordered).containsExactly(newYorkPrev, study2Prev, study5Prev, newYorkToday, study2Today,
					study5Today);
		}

	}

}
