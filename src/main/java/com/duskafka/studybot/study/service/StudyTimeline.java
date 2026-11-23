package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.time.StudyCalendar;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.StudyRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 어느 시각에 적용되는 규칙 버전과 기수를 고른다. 날짜 계산은 {@link StudyCalendar}에 맡긴다 (architecture.md 4.2).
 *
 * <p>규칙 버전이 바뀌는 날의 경계(OD-08)는 정하지 않았다. 지금은 '그 버전의 시간 규칙으로 센 스터디 일이 적용 시작일 이상인
 * 가장 늦은 버전'을 고른다. S05는 규칙 변경을 만들지 않아 버전은 늘 하나다.
 */
final class StudyTimeline {

	private StudyTimeline() {
	}

	/** 그 시각에 적용되는 규칙 버전. 스터디가 시작하기 전이면 첫 버전이다. */
	static StudyRule ruleAt(List<StudyRule> rulesByEffectiveFrom, ZoneId zoneId, Instant instant) {
		if (rulesByEffectiveFrom.isEmpty()) {
			throw new IllegalStateException("스터디에 규칙 버전이 없어요");
		}
		for (int i = rulesByEffectiveFrom.size() - 1; i >= 0; i--) {
			StudyRule rule = rulesByEffectiveFrom.get(i);
			if (!studyDayOf(rule, zoneId, instant).isBefore(rule.getEffectiveFrom())) {
				return rule;
			}
		}
		return rulesByEffectiveFrom.getFirst();
	}

	/** 그 규칙 버전으로 센, 시각이 속한 스터디 일. */
	static LocalDate studyDayOf(StudyRule rule, ZoneId zoneId, Instant instant) {
		return StudyCalendar.of(rule.toTimeRule(zoneId)).studyDayOf(instant);
	}

	/**
	 * 그 스터디 일의 기수. 첫 기수 전이면 첫 기수, 마지막 기수 뒤면 마지막 기수다.
	 * 기수 사이의 날('기수 밖')을 어떻게 다룰지는 OD-07이라 S05는 다음 기수를 만들지 않는다.
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

}
