package com.duskafka.studybot.progress;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * S09 테스트가 DB를 직접 준비하고 읽는 도우미. 사용자·스터디·규칙 버전·기수·멤버십·풀이 기록을 넣고, 멤버 일과 스터디별 집계를 읽는다.
 * 기수 행은 S12가 만들기 전이라 테스트가 직접 넣는다 (S09 만들 것 3).
 *
 * <p>테스트끼리 DB를 공유하므로 GitHub ID는 JVM 안에서 겹치지 않게 새로 받고(다른 테스트 도우미의 범위와 겹치지 않는 11,000,000부터),
 * 스터디는 테스트마다 새로 만든다.
 */
public final class StudyDayRows {

	/** 기본 스터디 시간대. */
	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	/** 기본 스터디의 규칙 버전 시작일이자 1기 시작일. */
	public static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

	/** 기본 스터디의 1기 마지막 날 (28일). */
	public static final LocalDate NOV_28 = LocalDate.of(2026, 11, 28);

	/** 멤버십의 가입 시각 기본값. 판정 시작일은 따로 넘긴다. */
	private static final Instant JOINED_AT = Instant.parse("2026-10-01T00:00:00Z");

	private static final AtomicLong NEXT_GITHUB_ID = new AtomicLong(11_000_000L);

	private final JdbcClient jdbc;

	public StudyDayRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 서울 시각을 Instant로. */
	public static Instant seoul(LocalDate date, int hour, int minute) {
		return date.atTime(hour, minute).atZone(SEOUL).toInstant();
	}

	/** 사용자를 넣고 ID를 돌려준다. */
	public long newUser(String login) {
		long githubId = NEXT_GITHUB_ID.incrementAndGet();
		return jdbc.sql("""
				insert into studybot.users (github_id, github_login, created_at)
				values (:githubId, :login, :createdAt) returning id
				""")
			.param("githubId", githubId)
			.param("login", login + "-" + githubId)
			.param("createdAt", Timestamp.from(JOINED_AT))
			.query(Long.class)
			.single();
	}

	/** 스터디 행만 넣는다 (방장은 새 사용자, 방장 멤버십은 넣지 않음). 규칙·기수·멤버십은 따로 넣는다. */
	public long newStudy(ZoneId zone) {
		long ownerId = newUser("owner");
		return jdbc.sql("""
				insert into studybot.studies (name, mode, owner_id, timezone, status, created_at)
				values ('S09 스터디', 'GROUP', :ownerId, :timezone, 'ACTIVE', :createdAt) returning id
				""")
			.param("ownerId", ownerId)
			.param("timezone", zone.getId())
			.param("createdAt", Timestamp.from(JOINED_AT))
			.query(Long.class)
			.single();
	}

	/**
	 * 규칙 버전을 넣는다. 시간·목표가 아닌 항목은 기본값이다.
	 *
	 * @param restDays 쉬는 요일 ({@code study_rules.rest_days} 형식, 예: {@code "FRIDAY"}. 없으면 빈 문자열)
	 */
	public long addRule(long studyId, LocalDate effectiveFrom, int dailyGoal, LocalTime dayBoundary, int graceMinutes,
			String restDays) {
		return jdbc.sql("""
				insert into studybot.study_rules
					(study_id, effective_from, daily_goal, day_boundary, grace_minutes, rest_days, penalty_type, fine_amount,
					exemptions_per_season, reminder_offset_minutes, self_report_mode, season_length_days, created_at)
				values (:studyId, :effectiveFrom, :dailyGoal, :dayBoundary, :grace, :restDays, 'NONE', 1000, 4, 120,
					'IMMEDIATE', 28, :createdAt)
				returning id
				""")
			.param("studyId", studyId)
			.param("effectiveFrom", effectiveFrom)
			.param("dailyGoal", dailyGoal)
			.param("dayBoundary", dayBoundary)
			.param("grace", graceMinutes)
			.param("restDays", restDays)
			.param("createdAt", Timestamp.from(JOINED_AT))
			.query(Long.class)
			.single();
	}

	/** 기수 행을 넣고 ID를 돌려준다. 상태는 ACTIVE다 (배정은 상태를 보지 않고 날짜로 본다). */
	public long addSeason(long studyId, int seq, LocalDate startDay, LocalDate endDay) {
		return jdbc.sql("""
				insert into studybot.seasons (study_id, seq, start_day, end_day, status, created_at)
				values (:studyId, :seq, :startDay, :endDay, 'ACTIVE', :createdAt) returning id
				""")
			.param("studyId", studyId)
			.param("seq", seq)
			.param("startDay", startDay)
			.param("endDay", endDay)
			.param("createdAt", Timestamp.from(JOINED_AT))
			.query(Long.class)
			.single();
	}

	/** 멤버십을 넣고 ID를 돌려준다 (역할 MEMBER). */
	public long join(long studyId, long userId, LocalDate judgedFrom) {
		return jdbc.sql("""
				insert into studybot.memberships (study_id, user_id, role, joined_at, judged_from)
				values (:studyId, :userId, 'MEMBER', :joinedAt, :judgedFrom) returning id
				""")
			.param("studyId", studyId)
			.param("userId", userId)
			.param("joinedAt", Timestamp.from(JOINED_AT))
			.param("judgedFrom", judgedFrom)
			.query(Long.class)
			.single();
	}

	/** 탈퇴시킨다 ({@code left_at}). */
	public void leave(long membershipId, Instant leftAt) {
		jdbc.sql("update studybot.memberships set left_at = :leftAt where id = :id")
			.param("leftAt", Timestamp.from(leftAt))
			.param("id", membershipId)
			.update();
	}

	/**
	 * S09 수용 기준의 기본 스터디: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 쉬는 요일 없음, 1기 11-01~11-28.
	 * 사용자를 11-01부터 판정하는 멤버로 넣는다.
	 */
	public TestStudy standardStudy(long memberId) {
		return standardStudy(memberId, SEOUL, LocalTime.MIDNIGHT, 30, "");
	}

	/** 기본 스터디에서 시간대·기준 시각·유예·쉬는 요일만 바꾼다. */
	public TestStudy standardStudy(long memberId, ZoneId zone, LocalTime dayBoundary, int graceMinutes, String restDays) {
		long studyId = newStudy(zone);
		addRule(studyId, NOV_1, 1, dayBoundary, graceMinutes, restDays);
		long seasonId = addSeason(studyId, 1, NOV_1, NOV_28);
		long membershipId = join(studyId, memberId, NOV_1);
		return new TestStudy(studyId, seasonId, membershipId);
	}

	/**
	 * 문제를 식별한 풀이 기록을 넣고 ID를 돌려준다. 배정은 출처를 보지 않으니 커밋·수신함이 없어도 되는 수동 인증 출처로 넣는다.
	 * 배정 연결 지점({@link StudyDayAssignment})은 부르지 않는다.
	 */
	public long solve(long userId, String platform, String problemNo, Instant receivedAt) {
		return jdbc.sql("""
				insert into studybot.solve_records (user_id, source, platform, problem_no, received_at, status)
				values (:userId, 'SELF_REPORT', :platform, :problemNo, :receivedAt, 'CONFIRMED') returning id
				""")
			.param("userId", userId)
			.param("platform", platform)
			.param("problemNo", problemNo)
			.param("receivedAt", Timestamp.from(receivedAt))
			.query(Long.class)
			.single();
	}

	/** 멤버 일 행을 바로 넣는다 (전날이 이미 판정됐거나, 면제권을 썼거나, 이미 센 상태를 준비할 때). */
	public void putMemberDay(long studyId, long userId, LocalDate studyDay, int solvedCount, @Nullable String result,
			@Nullable Instant exemptionUsedAt) {
		jdbc.sql("""
				insert into studybot.member_days (study_id, user_id, study_day, solved_count, result, exemption_used_at, judged_at)
				values (:studyId, :userId, :studyDay, :solvedCount, :result, :exemptionUsedAt,
					case when cast(:result as text) is null then null else cast(:createdAt as timestamptz) end)
				""")
			.param("studyId", studyId)
			.param("userId", userId)
			.param("studyDay", studyDay)
			.param("solvedCount", solvedCount)
			.param("result", result)
			.param("exemptionUsedAt", exemptionUsedAt == null ? null : Timestamp.from(exemptionUsedAt))
			.param("createdAt", Timestamp.from(JOINED_AT))
			.update();
	}

	/** 멤버 일 행. 없으면 빈 값. */
	public Optional<MemberDayRow> memberDay(long studyId, long userId, LocalDate studyDay) {
		return jdbc.sql("""
				select study_day, season_id, solved_count, exemption_used_at, result, version
				from studybot.member_days where study_id = :studyId and user_id = :userId and study_day = :studyDay
				""")
			.param("studyId", studyId)
			.param("userId", userId)
			.param("studyDay", studyDay)
			.query((rs, rowNum) -> new MemberDayRow(rs.getObject("study_day", LocalDate.class),
					rs.getObject("season_id", Long.class), rs.getInt("solved_count"),
					toInstant(rs.getObject("exemption_used_at", OffsetDateTime.class)), rs.getString("result"),
					rs.getInt("version")))
			.optional();
	}

	/** 그 날 센 문제 수 ({@code member_days.solved_count}). 행이 없으면 0. */
	public int solvedCount(long studyId, long userId, LocalDate studyDay) {
		return memberDay(studyId, userId, studyDay).map(MemberDayRow::solvedCount).orElse(0);
	}

	/** 스터디별 집계 행들. 스터디 일, ID 순서. */
	public List<StudyDaySolveRow> studyDaySolves(long studyId, long userId) {
		return jdbc.sql("""
				select study_day, solve_record_id, platform, problem_no
				from studybot.study_day_solves where study_id = :studyId and user_id = :userId
				order by study_day, id
				""")
			.param("studyId", studyId)
			.param("userId", userId)
			.query((rs, rowNum) -> new StudyDaySolveRow(rs.getObject("study_day", LocalDate.class),
					rs.getLong("solve_record_id"), rs.getString("platform"), rs.getString("problem_no")))
			.list();
	}

	/** 그 날의 스터디별 집계 행 수. */
	public int studyDaySolveCount(long studyId, long userId, LocalDate studyDay) {
		return (int) studyDaySolves(studyId, userId).stream().filter(row -> row.studyDay().equals(studyDay)).count();
	}

	private static @Nullable Instant toInstant(@Nullable OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}

	/** 테스트가 만든 스터디. */
	public record TestStudy(long studyId, long seasonId, long membershipId) {
	}

	/** 멤버 일 행. */
	public record MemberDayRow(LocalDate studyDay, @Nullable Long seasonId, int solvedCount,
			@Nullable Instant exemptionUsedAt, @Nullable String result, int version) {
	}

	/** 스터디별 집계 행. */
	public record StudyDaySolveRow(LocalDate studyDay, long solveRecordId, String platform, String problemNo) {
	}

}
