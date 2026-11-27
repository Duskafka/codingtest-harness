package com.duskafka.studybot.judgment;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * S12 테스트가 판정 결과·진행 지점·기수 행을 준비하고 읽는 도우미. 사용자·스터디·규칙·멤버십·풀이는
 * {@link com.duskafka.studybot.progress.StudyDayRows}로 넣는다.
 */
final class JudgmentRows {

	private final JdbcClient jdbc;

	JudgmentRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 판정 배치의 진행 지점을 정한다 ({@code studies.last_judged_day}). */
	void setLastJudgedDay(long studyId, @Nullable LocalDate day) {
		jdbc.sql("update studybot.studies set last_judged_day = :day where id = :id")
			.param("day", day)
			.param("id", studyId)
			.update();
	}

	/** 판정 배치의 진행 지점. */
	@Nullable LocalDate lastJudgedDay(long studyId) {
		return jdbc.sql("select last_judged_day from studybot.studies where id = :id")
			.param("id", studyId)
			.query((rs, rowNum) -> rs.getObject("last_judged_day", LocalDate.class))
			.list()
			.getFirst();
	}

	/** 멤버 일의 판정 값. 행이 없으면 빈 값. */
	Optional<Judged> judged(long studyId, long userId, LocalDate day) {
		return jdbc.sql("""
				select id, study_day, season_id, solved_count, result, result_source, judged_at, rule_id, version
				from studybot.member_days where study_id = :studyId and user_id = :userId and study_day = :day
				""")
			.param("studyId", studyId)
			.param("userId", userId)
			.param("day", day)
			.query((rs, rowNum) -> new Judged(rs.getLong("id"), rs.getObject("study_day", LocalDate.class),
					rs.getObject("season_id", Long.class), rs.getInt("solved_count"), rs.getString("result"),
					rs.getString("result_source"), toInstant(rs.getObject("judged_at", OffsetDateTime.class)),
					rs.getObject("rule_id", Long.class), rs.getInt("version")))
			.optional();
	}

	/** 판정 결과. 행이 없거나 미판정이면 {@code null}. */
	@Nullable String result(long studyId, long userId, LocalDate day) {
		return judged(studyId, userId, day).map(Judged::result).orElse(null);
	}

	/** 스터디의 멤버 일 행들. 스터디 일 순서. */
	List<Judged> judgedRows(long studyId, long userId) {
		return jdbc.sql("""
				select id, study_day, season_id, solved_count, result, result_source, judged_at, rule_id, version
				from studybot.member_days where study_id = :studyId and user_id = :userId order by study_day
				""")
			.param("studyId", studyId)
			.param("userId", userId)
			.query((rs, rowNum) -> new Judged(rs.getLong("id"), rs.getObject("study_day", LocalDate.class),
					rs.getObject("season_id", Long.class), rs.getInt("solved_count"), rs.getString("result"),
					rs.getString("result_source"), toInstant(rs.getObject("judged_at", OffsetDateTime.class)),
					rs.getObject("rule_id", Long.class), rs.getInt("version")))
			.list();
	}

	/** 기수 행을 상태를 정해 넣는다. */
	long insertSeason(long studyId, int seq, LocalDate startDay, LocalDate endDay, String status) {
		return jdbc.sql("""
				insert into studybot.seasons (study_id, seq, start_day, end_day, status, created_at)
				values (:studyId, :seq, :startDay, :endDay, :status, :createdAt) returning id
				""")
			.param("studyId", studyId)
			.param("seq", seq)
			.param("startDay", startDay)
			.param("endDay", endDay)
			.param("status", status)
			.param("createdAt", Timestamp.from(Instant.parse("2025-01-01T00:00:00Z")))
			.query(Long.class)
			.single();
	}

	/** 스터디의 기수 행들. 번호 순서. */
	List<SeasonRow> seasons(long studyId) {
		return jdbc.sql("""
				select id, seq, start_day, end_day, status, settling_started_at from studybot.seasons
				where study_id = :studyId order by seq
				""")
			.param("studyId", studyId)
			.query((rs, rowNum) -> new SeasonRow(rs.getLong("id"), rs.getInt("seq"),
					rs.getObject("start_day", LocalDate.class), rs.getObject("end_day", LocalDate.class),
					rs.getString("status"), toInstant(rs.getObject("settling_started_at", OffsetDateTime.class))))
			.list();
	}

	/** 스터디의 모든 규칙 버전의 기수 길이를 바꾼다. */
	void setSeasonLength(long studyId, int days) {
		jdbc.sql("update studybot.study_rules set season_length_days = :days where study_id = :studyId")
			.param("days", days)
			.param("studyId", studyId)
			.update();
	}

	/** 스터디의 모든 규칙 버전의 벌금 방식과 금액을 바꾼다. */
	void setPenalty(long studyId, String penaltyType, int fineAmount) {
		jdbc.sql("""
				update studybot.study_rules set penalty_type = :type, fine_amount = :amount where study_id = :studyId
				""")
			.param("type", penaltyType)
			.param("amount", fineAmount)
			.param("studyId", studyId)
			.update();
	}

	/** 규칙 버전 하나의 기준 시각·목표를 바꾼다. */
	void setRuleGoal(long ruleId, int dailyGoal) {
		jdbc.sql("update studybot.study_rules set daily_goal = :goal where id = :id")
			.param("goal", dailyGoal)
			.param("id", ruleId)
			.update();
	}

	private static @Nullable Instant toInstant(@Nullable OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}

	/** 멤버 일 한 행의 판정 값. */
	record Judged(long id, LocalDate studyDay, @Nullable Long seasonId, int solvedCount, @Nullable String result,
			@Nullable String resultSource, @Nullable Instant judgedAt, @Nullable Long ruleId, int version) {
	}

	/** 기수 한 행. */
	record SeasonRow(long id, int seq, LocalDate startDay, LocalDate endDay, String status,
			@Nullable Instant settlingStartedAt) {
	}

}
