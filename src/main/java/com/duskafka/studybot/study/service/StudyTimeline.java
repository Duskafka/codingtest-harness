package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.time.StudyCalendar;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.StudyRule;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 지금 적용되는 규칙 버전과 기수를 고른다. 날짜 계산은 {@link StudyCalendar}에 맡긴다 (architecture.md 4.2).
 *
 * <p>규칙 버전이 여럿일 때 어느 시각에 어느 버전을 쓸지(특히 기준 시각이 바뀌는 날)는 OD-08이라 정하지 않았다.
 * S05는 규칙 변경을 만들지 않아 버전이 늘 하나이고, 둘 이상이면 실패한다. 규칙 변경을 만드는 단계에서 바꾼다.
 */
final class StudyTimeline {

	private StudyTimeline() {
	}

	/** 스터디의 규칙 버전. OD-08 결정 전에는 하나뿐이다. */
	static StudyRule currentRule(List<StudyRule> rules) {
		if (rules.size() != 1) {
			throw new IllegalStateException(
					"규칙 버전이 " + rules.size() + "개예요. 버전이 여럿일 때의 경계는 OD-08 결정 뒤에 다뤄요");
		}
		return rules.getFirst();
	}

	/** 그 규칙 버전의 달력. */
	static StudyCalendar calendar(StudyRule rule, ZoneId zoneId) {
		return StudyCalendar.of(rule.toTimeRule(zoneId));
	}

	/**
	 * 그 스터디 일의 기수. 첫 기수 전이면 첫 기수, 마지막 기수가 끝난 뒤면 마지막 기수를 보여 준다 (S05 PR 판단 기록, 추측).
	 * 기수 사이·뒤의 날('기수 밖')을 어떻게 다룰지는 OD-07이라 S05는 다음 기수를 만들지 않는다.
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
