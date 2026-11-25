package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.common.time.StudyCalendar;
import com.duskafka.studybot.common.time.StudyTimeRule;
import com.duskafka.studybot.study.MembershipRole;
import com.duskafka.studybot.study.persistence.Membership;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.SeasonRepository;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import com.duskafka.studybot.study.persistence.StudyRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스터디 만들기 (S05 만들 것 1, 5.1 처리 순서 1). 규칙 1번 버전, 1기, 방장 멤버십을 한 트랜잭션으로 저장한다.
 *
 * <p>시작일은 사람이 정했다 (2026-11-23, S05 세션 "다음 날~30일 뒤"). 기본값은 다음 스터디 일이고, 다음 스터디 일부터
 * 오늘 스터디 일 + 30일까지 고를 수 있다. '오늘'은 새 스터디의 시간대와 기준 시각으로 센다.
 * 시작일이 규칙 v1의 적용 시작일, 1기 시작일, 방장의 판정 시작일이 된다. 방장도 멤버처럼 다음 스터디 일 이후부터 판정받는다 (R14).
 */
@Service
@RequiredArgsConstructor
public class StudyCreationService {

	private final StudyRepository studyRepository;

	private final StudyRuleRepository ruleRepository;

	private final SeasonRepository seasonRepository;

	private final MembershipRepository membershipRepository;

	private final Clock clock;

	/** 스터디를 만들고 ID를 돌려준다. 시간대·시작일이 맞지 않으면 400. */
	@Transactional
	public long create(long ownerId, NewStudy newStudy) {
		Instant now = this.clock.instant();
		ZoneId zoneId = parseZone(newStudy.timezone());
		RuleSettings rule = newStudy.rule();
		LocalDate start = startDate(newStudy, zoneId, now);

		Study study = this.studyRepository.save(new Study(newStudy.name(), newStudy.mode(), ownerId, zoneId, now));
		long studyId = study.getId();
		this.ruleRepository.save(rule.toVersion(studyId, start, now));
		this.seasonRepository
			.save(new Season(studyId, 1, start, StudyCalendar.seasonEndDay(start, rule.seasonLengthDays()), now));
		this.membershipRepository.save(new Membership(studyId, ownerId, MembershipRole.OWNER, now, start));
		return studyId;
	}

	private static LocalDate startDate(NewStudy newStudy, ZoneId zoneId, Instant now) {
		RuleSettings rule = newStudy.rule();
		StudyTimeRule timeRule;
		try {
			timeRule = new StudyTimeRule(zoneId, rule.dayBoundary(), rule.graceMinutes(), rule.reminderOffsetMinutes(),
					rule.restDays());
		}
		catch (IllegalArgumentException ex) {
			// 요청 검증이 같은 범위를 먼저 본다. 여기까지 오면 메시지(범위 안내)를 그대로 돌려준다.
			throw new BadRequestException(ex.getMessage());
		}
		StudyCalendar calendar = StudyCalendar.of(timeRule);
		LocalDate first = calendar.nextStudyDayOf(now);
		LocalDate last = calendar.studyDayOf(now).plusDays(StudyLimits.START_DATE_MAX_DAYS_AHEAD);
		LocalDate start = newStudy.startDate() != null ? newStudy.startDate() : first;
		if (start.isBefore(first) || start.isAfter(last)) {
			throw new BadRequestException("시작일은 " + first + "부터 " + last + "까지 고를 수 있어요.");
		}
		return start;
	}

	/** 지역 시간대 ID만 받는다 (예: Asia/Seoul). '+09:00' 같은 고정 오프셋은 서머타임 규칙이 없어 받지 않는다. */
	private static ZoneId parseZone(String timezone) {
		if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
			throw new BadRequestException("지원하지 않는 시간대예요. 'Asia/Seoul' 같은 지역 시간대 ID를 써 주세요.");
		}
		return ZoneId.of(timezone);
	}

}
