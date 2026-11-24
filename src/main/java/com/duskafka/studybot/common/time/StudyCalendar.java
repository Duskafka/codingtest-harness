package com.duskafka.studybot.common.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.util.Objects;

/**
 * 스터디 일, 유예, 알림 시각, 판정 시각 계산 (docs/spec/domain-rules.md 3.3, processes 5.5).
 * 시간 계산은 모두 여기서 한다 (architecture.md 4.2).
 *
 * <p>시간 규칙 하나({@link StudyTimeRule})로 계산하는 불변 값이고, 순수 함수만 있다. 현재 시각을 읽지 않으니
 * {@code Clock}도 필요 없다. 시각은 인자로 받는다.
 *
 * <ul>
 * <li>스터디 일 D는 {@code [D 기준 시각, D+1 기준 시각)}이다.</li>
 * <li>유예는 {@code [기준 시각, 기준 시각 + 유예)}다.</li>
 * <li>유예, 알림 오프셋, 판정 여유는 실제로 흐른 시간이다. 서머타임 전환일에도 벽시계가 아니라 분으로 센다.</li>
 * <li>날짜 계산은 스터디 시간대의 {@link ZonedDateTime}으로 한다 (5.10).</li>
 * </ul>
 *
 * <p>시간 규칙 하나로만 계산한다. 규칙 버전이 여럿이면 스터디 일은 {@link RuleVersionCalendar}로 센다 (기준 시각이 바뀌는 새 기수
 * 첫날, R2·OD-08). 유예·알림·판정 시각은 그날의 규칙으로 만든 이 달력으로 센다 (유예가 바뀐 날은 PR #20 열린 질문 3).
 */
public final class StudyCalendar {

	/** 판정 여유. 판정은 D+1 기준 시각 + 유예 + 이 시간 뒤에 한다 (R9). */
	public static final Duration JUDGE_MARGIN = Duration.ofMinutes(10);

	private final StudyTimeRule rule;

	private StudyCalendar(StudyTimeRule rule) {
		this.rule = rule;
	}

	public static StudyCalendar of(StudyTimeRule rule) {
		return new StudyCalendar(Objects.requireNonNull(rule, "rule"));
	}

	/**
	 * 시각이 속한 스터디 일. {@code boundaryOf(d) <= instant < boundaryOf(d + 1)}인 {@code d}다.
	 *
	 * <p>스터디 시간대의 날짜에서 시작해 경계와 비교하며 하루씩 옮긴다. 시각에서 기준 시각만큼 빼는 계산은
	 * 서머타임 전환일에 틀려서 쓰지 않는다. 기준 시각이 00:00~06:00이고 전환 폭이 몇 시간 안이라 한두 번 안에 끝난다.
	 */
	public LocalDate studyDayOf(Instant instant) {
		Objects.requireNonNull(instant, "instant");
		LocalDate day = instant.atZone(rule.zoneId()).toLocalDate();
		while (instant.isBefore(boundaryOf(day))) {
			day = day.minusDays(1);
		}
		while (!instant.isBefore(boundaryOf(day.plusDays(1)))) {
			day = day.plusDays(1);
		}
		return day;
	}

	/**
	 * 스터디 일이 시작하는 시각 (그날의 기준 시각).
	 *
	 * <p>서머타임 때문에 기준 시각이 그날 없거나 두 번 있으면 시계가 처음으로 기준 시각에 닿거나 지나는 순간을 쓴다.
	 * <ul>
	 * <li>두 번 있으면 앞의 것 ({@link ZonedDateTime#of}의 기본 해석과 같다)</li>
	 * <li>없으면 시계가 건너뛰는 전환 시점. 기본 해석은 빈 구간 길이만큼 뒤로 미루지만, 여기서는
	 * {@link LocalDate#atStartOfDay(ZoneId)}처럼 빈 구간이 끝나는 시각을 쓴다. 그래서 기준 시각이 00:00이면
	 * {@code atStartOfDay}와 결과가 같다.</li>
	 * </ul>
	 */
	public Instant boundaryOf(LocalDate studyDay) {
		Objects.requireNonNull(studyDay, "studyDay");
		ZoneId zone = rule.zoneId();
		LocalDateTime localBoundary = studyDay.atTime(rule.dayBoundary());
		ZoneOffsetTransition transition = zone.getRules().getTransition(localBoundary);
		if (transition != null && transition.isGap()) {
			return transition.getInstant();
		}
		return ZonedDateTime.of(localBoundary, zone).toInstant();
	}

	/** 시각이 그 스터디 일의 기준 시각부터 유예 안인지. 시작은 포함하고 끝은 뺀다. 유예가 0분이면 늘 아니다. */
	public boolean isInGrace(Instant instant) {
		Instant boundary = boundaryOf(studyDayOf(instant));
		return instant.isBefore(boundary.plus(rule.grace()));
	}

	/** 마감 전 알림 시각: D+1 기준 시각 − 알림 오프셋 (5.8). */
	public Instant reminderAt(LocalDate studyDay) {
		return boundaryOf(studyDay.plusDays(1)).minus(rule.reminderOffset());
	}

	/** 판정 시각: D+1 기준 시각 + 유예 + 판정 여유 10분 (R9). */
	public Instant judgeAt(LocalDate studyDay) {
		return boundaryOf(studyDay.plusDays(1)).plus(rule.grace()).plus(JUDGE_MARGIN);
	}

	/** 쉬는 요일인지. */
	public boolean isRestDay(LocalDate studyDay) {
		return rule.restDays().contains(studyDay.getDayOfWeek());
	}

	/** 시각이 속한 스터디 일의 다음 스터디 일. 가입하면 이날부터 판정한다 (R14). 새 스터디의 시작일 기본값도 이날이다 (S05). */
	public LocalDate nextStudyDayOf(Instant instant) {
		return studyDayOf(instant).plusDays(1);
	}

	/**
	 * 기수의 마지막 스터디 일(포함). 시작일부터 기수 길이만큼 운영한다 (R13). 28일이면 시작일 + 27일.
	 * 기수 경계일 계산은 여기 한 곳에서 한다 (5.13 '주의할 것'). 날짜만 세므로 시간 규칙과 관계없다.
	 * 다음 기수는 마지막 날 다음 날 바로 시작한다 (R13, OD-07). 그래서 다음 기수 시작일은 이 값 + 1이다.
	 */
	public static LocalDate seasonEndDay(LocalDate startDay, int lengthDays) {
		Objects.requireNonNull(startDay, "startDay");
		if (lengthDays < 1) {
			throw new IllegalArgumentException("기수 길이는 1일 이상이어야 해요: " + lengthDays);
		}
		return startDay.plusDays(lengthDays - 1L);
	}

}
