package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.time.RuleVersionCalendar;
import com.duskafka.studybot.common.time.StudyCalendar;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.StudyRule;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 그날 적용되는 규칙 버전과 기수를 고른다. 날짜 계산은 {@code common.time}에 맡긴다 (architecture.md 4.2).
 *
 * <p>규칙 버전 목록은 적용 시작일 순서로 받는다 ({@code StudyRuleRepository#findByStudyIdOrderByEffectiveFromAsc}).
 */
final class StudyTimeline {

	private StudyTimeline() {
	}

	/** 버전에 걸친 달력. 오늘·다음 스터디 일은 이것으로 센다 (R2, 기준 시각이 바뀌는 새 기수 첫날 포함). */
	static RuleVersionCalendar calendar(List<StudyRule> rulesByEffectiveFrom, ZoneId zoneId) {
		return RuleVersionCalendar.of(rulesByEffectiveFrom.stream()
			.map(rule -> new RuleVersionCalendar.Version(rule.getEffectiveFrom(), rule.toTimeRule(zoneId)))
			.toList());
	}

	/** 그 스터디 일에 적용되는 버전. 첫 버전보다 앞선 날(1기 시작 전)이면 첫 버전이다. */
	static StudyRule ruleOn(List<StudyRule> rulesByEffectiveFrom, LocalDate studyDay) {
		if (rulesByEffectiveFrom.isEmpty()) {
			throw new IllegalStateException("스터디에 규칙 버전이 없어요");
		}
		StudyRule found = rulesByEffectiveFrom.getFirst();
		for (StudyRule rule : rulesByEffectiveFrom) {
			if (rule.getEffectiveFrom().isAfter(studyDay)) {
				break;
			}
			found = rule;
		}
		return found;
	}

	/** {@code current} 뒤에 적용될 예정인 버전들 (상세 응답의 예정된 변경, 사람이 정함 2026-11-24 "현재 규칙 + 예정된 변경"). */
	static List<StudyRule> upcoming(List<StudyRule> rulesByEffectiveFrom, StudyRule current) {
		return rulesByEffectiveFrom.stream()
			.filter(rule -> rule.getEffectiveFrom().isAfter(current.getEffectiveFrom()))
			.toList();
	}

	/**
	 * 그 스터디 일의 기수. 1기 전이면 1기, 마지막 기수 행이 끝난 뒤면 마지막 기수를 보여 준다. 기수 밖인 날은 판정하지 않으니
	 * (R9, OD-07) 화면에 보여 줄 기수로만 쓴다. 다음 기수 행을 만드는 것은 S12다.
	 */
	static Season seasonOn(List<Season> seasonsBySeq, LocalDate studyDay) {
		if (seasonsBySeq.isEmpty()) {
			throw new IllegalStateException("스터디에 기수가 없어요");
		}
		for (Season season : seasonsBySeq) {
			if (season.contains(studyDay)) {
				return season;
			}
		}
		Season first = seasonsBySeq.getFirst();
		return studyDay.isBefore(first.getStartDay()) ? first : seasonsBySeq.getLast();
	}

	/**
	 * 오늘이 속한 기수 다음 기수의 시작일. 다음 기수는 마지막 날 다음 날 바로 시작한다 (R13, OD-07). 1기 시작 전이면 1기 시작일이다.
	 *
	 * <p>다음 기수 행은 S12가 만든다. 그래서 오늘이 마지막 기수 행보다 뒤면, 마지막 행의 다음 날부터 그 시작일에 적용되는 규칙의 기수
	 * 길이로 기수를 이어 센다 (끝 날짜는 {@link StudyCalendar#seasonEndDay}, 5.13).
	 */
	static LocalDate nextSeasonStart(List<Season> seasonsBySeq, List<StudyRule> rulesByEffectiveFrom, LocalDate today) {
		Season first = seasonsBySeq.getFirst();
		if (today.isBefore(first.getStartDay())) {
			return first.getStartDay();
		}
		for (Season season : seasonsBySeq) {
			if (season.contains(today)) {
				return season.getEndDay().plusDays(1);
			}
		}
		LocalDate start = seasonsBySeq.getLast().getEndDay().plusDays(1);
		while (true) {
			LocalDate end = StudyCalendar.seasonEndDay(start, ruleOn(rulesByEffectiveFrom, start).getSeasonLengthDays());
			if (!today.isAfter(end)) {
				return end.plusDays(1);
			}
			start = end.plusDays(1);
		}
	}

}
