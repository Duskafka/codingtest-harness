package com.duskafka.studybot.study.service;

import com.duskafka.studybot.study.MembershipRole;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRule;
import java.util.List;

/**
 * 스터디 상세 (api.md 4.4 '현재 규칙, 기수'). 조회 트랜잭션이 끝난 뒤 읽기만 한다.
 *
 * @param study 스터디
 * @param myRole 요청한 사람의 역할
 * @param memberCount 탈퇴하지 않은 멤버 수 (방장 포함)
 * @param currentRule 지금 적용되는 규칙 버전 (시작 전이면 첫 버전)
 * @param upcomingRules 아직 적용되지 않은 예정 버전. 적용 시작일 순서 (R2: 다음 스터디 일부터 / 다음 기수부터)
 * @param currentSeason 지금 기수 (시작 전이면 1기)
 */
public record StudyDetail(Study study, MembershipRole myRole, long memberCount, StudyRule currentRule,
		List<StudyRule> upcomingRules, Season currentSeason) {

	public StudyDetail {
		upcomingRules = List.copyOf(upcomingRules);
	}

}
