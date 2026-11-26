package com.duskafka.studybot.study;

import com.duskafka.studybot.common.time.RuleVersionCalendar;
import com.duskafka.studybot.common.time.StudyCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 사용자가 속한(탈퇴하지 않은) 스터디 하나에서, 그 사용자의 풀이를 스터디 일에 배정하는 데 필요한 값 ({@link MemberStudies}, S09).
 * 날짜 계산은 {@code common.time}에 맡긴다 (architecture.md 4.2).
 *
 * <ul>
 * <li>스터디 일·유예 구간은 규칙 버전에 걸친 달력({@link RuleVersionCalendar})으로 센다 (R2, R4).</li>
 * <li>그날의 하루 목표와 쉬는 요일은 그날의 규칙 버전으로 본다. 전날의 목표는 전날의 규칙이다 (5.5 '배정 함수', S09 만들 것 1).</li>
 * <li>기수 안인지는 기수 행의 날짜로 본다 (5.13 '주의할 것', S09 만들 것 3). 상태값은 보지 않는다.</li>
 * </ul>
 *
 * @param studyId 스터디 ID
 * @param judgedFrom 이 멤버를 판정하기 시작하는 스터디 일 (R14: 가입한 다음 스터디 일)
 * @param calendar 규칙 버전에 걸친 달력
 * @param goals 규칙 버전마다의 하루 목표. 하나 이상
 * @param seasons 스터디의 기수 행들
 */
public record MemberStudy(long studyId, LocalDate judgedFrom, RuleVersionCalendar calendar, List<DailyGoal> goals,
		List<SeasonPeriod> seasons) {

	/**
	 * 규칙 버전 하나의 하루 목표.
	 *
	 * @param effectiveFrom 버전을 적용하기 시작하는 스터디 일
	 * @param dailyGoal 하루 목표 문제 수 (R1)
	 */
	public record DailyGoal(LocalDate effectiveFrom, int dailyGoal) {

		public DailyGoal {
			Objects.requireNonNull(effectiveFrom, "effectiveFrom");
		}

	}

	/**
	 * 기수 행 하나의 기간 (양 끝 포함).
	 *
	 * @param seasonId 기수 행 ID ({@code seasons.id})
	 * @param startDay 시작일
	 * @param endDay 마지막 날
	 */
	public record SeasonPeriod(long seasonId, LocalDate startDay, LocalDate endDay) {

		public SeasonPeriod {
			Objects.requireNonNull(startDay, "startDay");
			Objects.requireNonNull(endDay, "endDay");
		}

		boolean contains(LocalDate day) {
			return !day.isBefore(this.startDay) && !day.isAfter(this.endDay);
		}

	}

	public MemberStudy {
		Objects.requireNonNull(judgedFrom, "judgedFrom");
		Objects.requireNonNull(calendar, "calendar");
		Objects.requireNonNull(goals, "goals");
		Objects.requireNonNull(seasons, "seasons");
		if (goals.isEmpty()) {
			throw new IllegalArgumentException("규칙 버전의 하루 목표가 하나 이상 있어야 해요");
		}
		goals = goals.stream().sorted(Comparator.comparing(DailyGoal::effectiveFrom)).toList();
		seasons = List.copyOf(seasons);
	}

	/** 시각이 속한 스터디 일 (R4의 N). */
	public LocalDate studyDayOf(Instant instant) {
		return this.calendar.studyDayOf(instant);
	}

	/** 시각이 그 스터디 일이 시작한 뒤 유예 안인지. 유예는 그날(N)의 규칙이다 (R4). */
	public boolean isInGrace(Instant instant) {
		return this.calendar.isInGrace(instant);
	}

	/** 그 스터디 일의 하루 목표. 그날의 규칙 버전으로 본다. 첫 버전보다 앞선 날은 첫 버전이다. */
	public int dailyGoalOn(LocalDate studyDay) {
		Objects.requireNonNull(studyDay, "studyDay");
		DailyGoal found = this.goals.getFirst();
		for (DailyGoal goal : this.goals) {
			if (goal.effectiveFrom().isAfter(studyDay)) {
				break;
			}
			found = goal;
		}
		return found.dailyGoal();
	}

	/** 그 스터디 일이 쉬는 요일인지. 그날의 규칙 버전으로 본다. */
	public boolean isRestDay(LocalDate studyDay) {
		return StudyCalendar.of(this.calendar.ruleFor(studyDay)).isRestDay(studyDay);
	}

	/** 그 스터디 일을 담은 기수 행의 ID. 기수 밖인 날(1기 시작 전 등)이면 {@code null}이다. */
	public @Nullable Long seasonIdOn(LocalDate studyDay) {
		SeasonPeriod season = seasonOn(studyDay);
		return season == null ? null : season.seasonId();
	}

	/** 그 스터디 일을 담은 기수 행의 기간. 기수 밖인 날이면 {@code null}이다 (S11: 면제권 한도는 이 기간에 센다). */
	public @Nullable SeasonPeriod seasonOn(LocalDate studyDay) {
		Objects.requireNonNull(studyDay, "studyDay");
		for (SeasonPeriod season : this.seasons) {
			if (season.contains(studyDay)) {
				return season;
			}
		}
		return null;
	}

	/**
	 * 이 멤버를 그 스터디 일에 판정하는지 (R9 '판정 대상'): 판정 시작일({@code judgedFrom}) 이후이고 기수 안인 날. 탈퇴하지 않은 멤버십만
	 * 다루므로 탈퇴한 날은 보지 않는다 ({@link MemberStudies}). 쉬는 요일도 판정은 하므로(면제) 여기서 보지 않는다.
	 */
	public boolean isJudgedOn(LocalDate studyDay) {
		return !studyDay.isBefore(this.judgedFrom) && seasonIdOn(studyDay) != null;
	}

}
