package com.duskafka.studybot.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 규칙 버전이 여럿인 스터디의 스터디 일 계산 (R2, OD-08, S05). 시간 계산은 {@code common}의 이 패키지에서만 한다
 * (architecture.md 4.2).
 *
 * <ul>
 * <li>그날의 규칙({@link #ruleFor})은 적용 시작일이 그날 이전(같은 날 포함)인 마지막 버전이다. 첫 버전보다 앞선 날은 첫 버전을 쓴다.</li>
 * <li>스터디 일 D는 {@code [startOf(D), startOf(D+1))}이다. {@code startOf(D)}는 <b>D−1의 규칙</b>으로 센 D의 기준 시각이다.
 * 그래서 하루 기준 시각이 바뀌는 날, 지난 버전의 마지막 날은 옛 기준 시각에 끝나고, 새 버전의 첫날이 그때 시작해 새 기준
 * 시각에 끝난다. 새 첫날이 길이 차이를 떠안아 18~30시간이 된다 (R2 '새 기수 첫날', 사람이 정함 2026-11-24).</li>
 * <li>버전이 하나면 {@link StudyCalendar}와 같다. 하루 기준 시각은 {@link StudyCalendar#boundaryOf}로 센다(서머타임 처리 포함).</li>
 * <li>유예 구간과 판정 시각은 유예 구간이 시작되는 날의 규칙(바뀐 뒤)의 유예를 쓴다 (사람이 정함, 2026-11-24 "바꾼 뒤의 유예").
 * 날 D의 판정 시각은 {@code startOf(D+1) + D+1의 유예 + 10분}이다.</li>
 * </ul>
 *
 * <p>알림 시각은 여기서 다루지 않는다. 알림 오프셋이 바뀐 날 어느 버전을 쓸지는 정하지 않았다 (S13, PR #21 열린 질문).
 */
public final class RuleVersionCalendar {

	/**
	 * 규칙 버전 하나의 시간 규칙.
	 *
	 * @param effectiveFrom 이 버전을 적용하기 시작하는 스터디 일
	 * @param rule 시간 규칙
	 */
	public record Version(LocalDate effectiveFrom, StudyTimeRule rule) {

		public Version {
			Objects.requireNonNull(effectiveFrom, "effectiveFrom");
			Objects.requireNonNull(rule, "rule");
		}

	}

	private final List<Version> versions;

	private final ZoneId zoneId;

	private RuleVersionCalendar(List<Version> versions) {
		this.versions = versions;
		this.zoneId = versions.getFirst().rule().zoneId();
	}

	/**
	 * @param versions 규칙 버전들. 순서는 상관없다. 하나 이상이고, 적용 시작일이 겹치지 않고, 시간대가 모두 같아야 한다
	 * (스터디 시간대는 바꾸지 않는다, 5.1)
	 */
	public static RuleVersionCalendar of(List<Version> versions) {
		Objects.requireNonNull(versions, "versions");
		if (versions.isEmpty()) {
			throw new IllegalArgumentException("규칙 버전이 하나 이상 있어야 해요");
		}
		List<Version> sorted = versions.stream().sorted(Comparator.comparing(Version::effectiveFrom)).toList();
		ZoneId zone = sorted.getFirst().rule().zoneId();
		for (int i = 0; i < sorted.size(); i++) {
			if (!sorted.get(i).rule().zoneId().equals(zone)) {
				throw new IllegalArgumentException("규칙 버전의 시간대가 서로 달라요");
			}
			if (i > 0 && sorted.get(i).effectiveFrom().equals(sorted.get(i - 1).effectiveFrom())) {
				throw new IllegalArgumentException("적용 시작일이 같은 규칙 버전이 둘이에요: " + sorted.get(i).effectiveFrom());
			}
		}
		return new RuleVersionCalendar(sorted);
	}

	/** 그 스터디 일에 적용되는 시간 규칙. 첫 버전보다 앞선 날이면 첫 버전이다. */
	public StudyTimeRule ruleFor(LocalDate studyDay) {
		Objects.requireNonNull(studyDay, "studyDay");
		Version found = this.versions.getFirst();
		for (Version version : this.versions) {
			if (version.effectiveFrom().isAfter(studyDay)) {
				break;
			}
			found = version;
		}
		return found.rule();
	}

	/** 스터디 일이 시작하는 시각. 전날의 규칙으로 센 그날의 기준 시각이다. */
	public Instant startOf(LocalDate studyDay) {
		Objects.requireNonNull(studyDay, "studyDay");
		return StudyCalendar.of(ruleFor(studyDay.minusDays(1))).boundaryOf(studyDay);
	}

	/**
	 * 시각이 속한 스터디 일. {@code startOf(d) <= instant < startOf(d + 1)}인 {@code d}다. 스터디 시간대의 날짜에서 시작해
	 * 하루씩 옮긴다 ({@link StudyCalendar#studyDayOf}와 같은 방식). 하루는 18시간 이상이라 한두 번 안에 끝난다.
	 */
	public LocalDate studyDayOf(Instant instant) {
		Objects.requireNonNull(instant, "instant");
		LocalDate day = instant.atZone(this.zoneId).toLocalDate();
		while (instant.isBefore(startOf(day))) {
			day = day.minusDays(1);
		}
		while (!instant.isBefore(startOf(day.plusDays(1)))) {
			day = day.plusDays(1);
		}
		return day;
	}

	/**
	 * 시각이 그 스터디 일이 시작한 뒤 유예 안인지. 유예는 그 구간이 시작되는 날(바뀐 뒤)의 규칙을 쓴다 (사람이 정함, 2026-11-24
	 * "바꾼 뒤의 유예", 부록 D {@code assignStudyDay}의 {@code rule(N).grace}). 시작은 포함하고 끝은 뺀다.
	 */
	public boolean isInGrace(Instant instant) {
		LocalDate day = studyDayOf(instant);
		return instant.isBefore(startOf(day).plus(ruleFor(day).grace()));
	}

	/**
	 * 그 스터디 일의 늦은 풀이를 받아 줄 수 있는 유예 구간이 끝나는 시각: 다음 날 시작 + 다음 날 규칙의 유예 (R4, {@link #isInGrace}).
	 * 이 시각 전에 온 풀이는 조건이 맞으면 그날로 간다 (R4). 끝 시각 자체는 유예 밖이다.
	 */
	public Instant graceEndOf(LocalDate studyDay) {
		LocalDate next = studyDay.plusDays(1);
		return startOf(next).plus(ruleFor(next).grace());
	}

	/**
	 * 판정 시각: 다음 날 시작 + 다음 날 규칙의 유예 + 판정 여유 10분 (R9). 그날의 늦은 풀이를 받아 주는 유예 구간과 같은 유예를 쓴다
	 * ({@link #graceEndOf}).
	 */
	public Instant judgeAt(LocalDate studyDay) {
		return graceEndOf(studyDay).plus(StudyCalendar.JUDGE_MARGIN);
	}

	/** 마감 전 알림 시각 (S13). 테스트를 먼저 옮기려는 껍데기다. */
	public Instant reminderAt(LocalDate studyDay) {
		throw new UnsupportedOperationException("S13");
	}

	/** 시각이 속한 스터디 일의 다음 스터디 일. 가입하면 이날부터 판정하고(R14), 규칙 변경은 이날부터 적용한다(R2). */
	public LocalDate nextStudyDayOf(Instant instant) {
		return studyDayOf(instant).plusDays(1);
	}

}
