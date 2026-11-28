package com.duskafka.studybot.study;

import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.common.time.RuleVersionCalendar;
import com.duskafka.studybot.common.time.StudyCalendar;
import com.duskafka.studybot.study.StudyDayContext.RosterMember;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.SeasonRepository;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import com.duskafka.studybot.study.persistence.StudyRule;
import com.duskafka.studybot.study.persistence.StudyRuleRepository;
import com.duskafka.studybot.study.service.StudyTimeline;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판정 배치가 판정할 날과 대상을 고르고 진행 지점을 올린다 (S12 만들 것 2·3·5, 5.10). judgment가 부른다 (judgment → study,
 * architecture.md 4.2). 멤버 일은 progress가, 판정 규칙은 judgment가 다룬다.
 */
@Component
@RequiredArgsConstructor
public class JudgingDays {

	private final StudyRepository studyRepository;

	private final StudyRuleRepository ruleRepository;

	private final SeasonRepository seasonRepository;

	private final MembershipRepository membershipRepository;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	/** 판정할 스터디: 활성 스터디, ID 순서. */
	@Transactional(readOnly = true)
	public List<Long> activeStudyIds() {
		return this.studyRepository.findActiveIds();
	}

	/**
	 * 다음에 판정할 날. 판정 시각이 아직 지나지 않았으면 빈 값이다.
	 *
	 * <ul>
	 * <li>다음에 판정할 날은 진행 지점({@code last_judged_day}) 다음 날이다. 비어 있으면 1기 시작일이다. 1기 시작 전 날은 기수 밖이라 판정하지
	 * 않는다 (R9, OD-07). '어제'가 아니라 아직 판정하지 않은 날을 오래된 순서로 하나씩 고른다 (5.10 처리 순서 2).</li>
	 * <li>판정 시각은 {@link RuleVersionCalendar#judgeAt}(= 다음 날 시작 + 다음 날 규칙의 유예 + 10분, R9)이다.</li>
	 * <li>규칙(목표·쉬는 요일)은 그날의 규칙 버전이다. 오늘 규칙으로 지난 날을 판정하지 않는다 (T11).</li>
	 * <li>대상은 그날 멤버였던 멤버십 중 그날을 판정하는 사람이다: 판정 시작일 이후, 탈퇴한 날까지, 기수 안 (R9, R14). 같은 날 탈퇴하고 다시 가입한
	 * 사람은 한 번만 들어간다. 오늘 현황과 같은 방식이다 ({@link StudyDays#load}).</li>
	 * </ul>
	 *
	 * <p>쿼리는 스터디·기수·규칙 버전·멤버십 한 번씩이다. 멤버 수와 상관없다 (S12 수용 기준 '쿼리 수').
	 */
	@Transactional(readOnly = true)
	public Optional<JudgingDay> nextDue(long studyId) {
		Study study = this.studyRepository.findById(studyId)
			.orElseThrow(() -> new NotFoundException(StudyAccess.NOT_FOUND_DETAIL));
		List<Season> seasons = this.seasonRepository.findByStudyIdOrderBySeqAsc(studyId);
		if (seasons.isEmpty()) {
			return Optional.empty();
		}
		List<StudyRule> rules = this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(studyId);
		RuleVersionCalendar calendar = StudyTimeline.calendar(rules, study.zoneId());
		LocalDate lastJudged = study.getLastJudgedDay();
		LocalDate day = lastJudged == null ? seasons.getFirst().getStartDay() : lastJudged.plusDays(1);
		if (calendar.judgeAt(day).isAfter(this.clock.instant())) {
			return Optional.empty();
		}
		Season season = seasons.stream().filter(row -> row.contains(day)).findFirst().orElse(null);
		List<Long> targets = season == null ? List.of()
				: StudyDays
					.rosterOf(this.membershipRepository.findMembersDuring(studyId, calendar.startOf(day),
							calendar.startOf(day.plusDays(1))), day, true)
					.stream()
					.filter(RosterMember::judged)
					.map(RosterMember::userId)
					.toList();
		StudyRule rule = StudyTimeline.ruleOn(rules, day);
		return Optional.of(new JudgingDay(studyId, day, rule.getId(), rule.getDailyGoal(),
				StudyCalendar.of(calendar.ruleFor(day)).isRestDay(day), season == null ? null : season.getId(), targets));
	}

	/**
	 * 하루치 판정을 모두 마쳤을 때 진행 지점을 그날로 올린다 (5.10 처리 순서 3, S12 만들 것 5). 마지막 날까지 판정한 진행 기수는 정산 중이 되고
	 * 그 시각을 남긴다 (R13, 3.4 'ACTIVE → SETTLING'). 판정 결과·진행 지점 저장은 스터디 행 잠금의 범위가 아니다 (결정 제안서 006).
	 *
	 * <p>진행 지점을 실제로 올렸으면 같은 트랜잭션에서 {@link StudyDayJudged}를 발행한다 (S14 만들 것 5, 결정 제안서 010). 받는 쪽(결과
	 * 알림)이 실패하면 이 트랜잭션도 롤백돼 진행 지점이 올라가지 않는다. 이미 올라가 있었으면(두 실행이 겹침) 발행하지 않는다.
	 *
	 * @return 진행 지점을 올렸는지. 이미 그날이거나 그 뒤면 거짓이다
	 */
	@Transactional
	public boolean markJudged(long studyId, LocalDate day) {
		if (this.studyRepository.advanceLastJudgedDay(studyId, day) == 0) {
			return false;
		}
		this.seasonRepository.markSettling(studyId, day, this.clock.instant());
		Study study = this.studyRepository.findById(studyId)
			.orElseThrow(() -> new NotFoundException(StudyAccess.NOT_FOUND_DETAIL));
		RuleVersionCalendar calendar = StudyTimeline
			.calendar(this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(studyId), study.zoneId());
		this.events.publishEvent(new StudyDayJudged(studyId, study.getName(), study.getMode(), study.getOwnerId(), day,
				calendar.startOf(day.plusDays(2))));
		return true;
	}

}
