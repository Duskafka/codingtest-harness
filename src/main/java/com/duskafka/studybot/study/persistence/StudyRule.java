package com.duskafka.studybot.study.persistence;

import com.duskafka.studybot.common.time.StudyTimeRule;
import com.duskafka.studybot.study.PenaltyType;
import com.duskafka.studybot.study.SelfReportMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 스터디 규칙의 한 버전 (data-model.md 4.3 {@code study_rules}, R1, R2). 그날부터의 규칙 전체를 담는다. 적용된 버전은 고치지 않고,
 * 규칙을 바꾸면 아직 적용 전인 예정 버전만 지우고 다시 만든다 ({@code study.service.StudyRuleService}).
 * 범위는 R1 표이고, 요청 검증과 DB CHECK가 지킨다.
 */
@Entity
@Table(name = "study_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "study_id", nullable = false)
	private Long studyId;

	/** 이 버전을 적용하기 시작하는 스터디 일 (R2). 첫 버전은 스터디 시작일이다. */
	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "daily_goal", nullable = false)
	private int dailyGoal;

	@Column(name = "day_boundary", nullable = false)
	private LocalTime dayBoundary;

	@Column(name = "grace_minutes", nullable = false)
	private int graceMinutes;

	/** 쉬는 요일. DayOfWeek 이름을 월요일부터 쉼표로 이은 값. {@link #restDays()}로 읽는다. */
	@Getter(AccessLevel.NONE)
	@Column(name = "rest_days", nullable = false)
	private String restDays;

	@Enumerated(EnumType.STRING)
	@Column(name = "penalty_type", nullable = false)
	private PenaltyType penaltyType;

	@Column(name = "fine_amount", nullable = false)
	private int fineAmount;

	/** 보증금액. R1에 범위가 없어 S05에서는 받지 않는다 (S05 PR 열린 질문). */
	@Column(name = "deposit_amount")
	private @Nullable Integer depositAmount;

	@Column(name = "exemptions_per_season", nullable = false)
	private int exemptionsPerSeason;

	@Column(name = "reminder_offset_minutes", nullable = false)
	private int reminderOffsetMinutes;

	@Enumerated(EnumType.STRING)
	@Column(name = "self_report_mode", nullable = false)
	private SelfReportMode selfReportMode;

	@Column(name = "season_length_days", nullable = false)
	private int seasonLengthDays;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Builder
	private StudyRule(long studyId, LocalDate effectiveFrom, int dailyGoal, LocalTime dayBoundary, int graceMinutes,
			Set<DayOfWeek> restDays, PenaltyType penaltyType, int fineAmount, int exemptionsPerSeason,
			int reminderOffsetMinutes, SelfReportMode selfReportMode, int seasonLengthDays, Instant createdAt) {
		this.studyId = studyId;
		this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
		this.dailyGoal = dailyGoal;
		this.dayBoundary = Objects.requireNonNull(dayBoundary, "dayBoundary");
		this.graceMinutes = graceMinutes;
		this.restDays = format(Objects.requireNonNull(restDays, "restDays"));
		this.penaltyType = Objects.requireNonNull(penaltyType, "penaltyType");
		this.fineAmount = fineAmount;
		this.exemptionsPerSeason = exemptionsPerSeason;
		this.reminderOffsetMinutes = reminderOffsetMinutes;
		this.selfReportMode = Objects.requireNonNull(selfReportMode, "selfReportMode");
		this.seasonLengthDays = seasonLengthDays;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	/** 쉬는 요일. 월요일부터 순서대로. */
	public Set<DayOfWeek> restDays() {
		if (this.restDays.isEmpty()) {
			return EnumSet.noneOf(DayOfWeek.class);
		}
		return Arrays.stream(this.restDays.split(","))
			.map(DayOfWeek::valueOf)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
	}

	/** 이 버전의 시간 규칙. 날짜 계산은 이 값으로 {@code StudyCalendar}에서 한다 (architecture.md 4.2). */
	public StudyTimeRule toTimeRule(ZoneId zoneId) {
		return new StudyTimeRule(zoneId, this.dayBoundary, this.graceMinutes, this.reminderOffsetMinutes, restDays());
	}

	private static String format(Set<DayOfWeek> days) {
		return days.stream()
			.sorted(Comparator.naturalOrder())
			.map(DayOfWeek::name)
			.collect(Collectors.joining(","));
	}

}
