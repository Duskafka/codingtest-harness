package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.common.time.StudyCalendar;
import com.duskafka.studybot.study.StudyAccess;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.SeasonRepository;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import com.duskafka.studybot.study.persistence.StudyRule;
import com.duskafka.studybot.study.persistence.StudyRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 규칙 변경 (S05 만들 것 4, R2, OD-07, OD-08, T11). 권한은 방장이다 (api.md 4.5).
 *
 * <p>요청은 원하는 규칙 전체다 (사람이 정함, 2026-11-24 "규칙 전체를 받음"). 아직 적용되지 않은 예정 버전을 지우고 원하는 규칙에
 * 맞춰 다시 만든다. 이미 적용된 버전(오늘까지)은 고치지 않는다 (과거 판정에 소급하지 않음).
 *
 * <ul>
 * <li><b>1기 시작 전</b>(오늘 &lt; 1기 시작일): 아직 판정한 날이 없어 모든 항목을 1기 시작일부터 적용한다. 1기 끝 날짜도 새 기수
 * 길이로 다시 센다 (사람이 정함, 2026-11-24 "모두 1기 시작일부터").</li>
 * <li><b>기수 중</b>: 다음 스터디 일 항목은 다음 스터디 일부터, 다음 기수 항목은 다음 기수 시작일(오늘이 속한 기수의 끝 + 1)부터
 * ({@link RuleSettings}). 오늘이 기수 마지막 날이면 두 날이 같아 버전 하나다. 지금 규칙과 같은 종류는 버전을 만들지 않으니,
 * 지금 규칙과 같게 보내면 예정된 변경이 없어진다.</li>
 * </ul>
 *
 * <p>버전 행은 그날부터의 규칙 전체를 담는다(스냅샷). 테이블은 그대로다 ({@code (study_id, effective_from)} UNIQUE).
 * 같은 스터디의 규칙 변경·수락·위임은 스터디 행 잠금으로 차례로 실행된다 (결정 제안서 006).
 */
@Service
@RequiredArgsConstructor
public class StudyRuleService {

	private final StudyAccess studyAccess;

	private final StudyRepository studyRepository;

	private final StudyRuleRepository ruleRepository;

	private final SeasonRepository seasonRepository;

	private final Clock clock;

	@Transactional
	public void change(long studyId, long actorUserId, RuleSettings desired) {
		Study study = this.studyRepository.findByIdForUpdate(studyId)
			.orElseThrow(() -> new NotFoundException("스터디를 찾을 수 없어요."));
		this.studyAccess.requireOwner(studyId, actorUserId);
		List<StudyRule> rules = this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(studyId);
		List<Season> seasons = this.seasonRepository.findByStudyIdOrderBySeqAsc(studyId);
		Instant now = this.clock.instant();
		LocalDate today = StudyTimeline.calendar(rules, study.zoneId()).studyDayOf(now);
		Season first = seasons.getFirst();

		if (today.isBefore(first.getStartDay())) {
			LocalDate start = first.getStartDay();
			this.ruleRepository.deleteFrom(studyId, start);
			this.ruleRepository.save(desired.toVersion(studyId, start, now));
			this.seasonRepository.rescheduleEnd(first.getId(),
					StudyCalendar.seasonEndDay(start, desired.seasonLengthDays()));
			return;
		}

		LocalDate nextDay = today.plusDays(1);
		LocalDate nextSeasonStart = StudyTimeline.nextSeasonStart(seasons, rules, today);
		RuleSettings current = RuleSettings.of(StudyTimeline.ruleOn(rules, today));
		this.ruleRepository.deleteFrom(studyId, nextDay);
		if (nextSeasonStart.equals(nextDay)) {
			if (!desired.equals(current)) {
				this.ruleRepository.save(desired.toVersion(studyId, nextDay, now));
			}
			return;
		}
		if (!desired.sameNextDayItems(current)) {
			this.ruleRepository.save(desired.withNextSeasonItemsOf(current).toVersion(studyId, nextDay, now));
		}
		if (!desired.sameNextSeasonItems(current)) {
			this.ruleRepository.save(desired.toVersion(studyId, nextSeasonStart, now));
		}
	}

}
