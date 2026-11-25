package com.duskafka.studybot.study;

import com.duskafka.studybot.common.time.RuleVersionCalendar;
import com.duskafka.studybot.study.persistence.Membership;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import com.duskafka.studybot.study.persistence.SeasonRepository;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import com.duskafka.studybot.study.persistence.StudyRule;
import com.duskafka.studybot.study.persistence.StudyRuleRepository;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 속한 스터디를 배정에 필요한 값({@link MemberStudy})으로 돌려준다. progress가 풀이를 스터디 일에 배정할 때 부른다
 * (progress → study, architecture.md 4.2, S09 만들 것 5).
 */
@Component
@RequiredArgsConstructor
public class MemberStudies {

	private final MembershipRepository membershipRepository;

	private final StudyRepository studyRepository;

	private final StudyRuleRepository ruleRepository;

	private final SeasonRepository seasonRepository;

	/**
	 * 사용자가 속한 활성 스터디. 탈퇴하지 않은 멤버십만 고른다. 탈퇴한 뒤 들어온 풀이는 탈퇴한 날을 포함해 어느 날에도 세지 않는다
	 * (사람이 정함, 2026-11-25 S09 세션 "활성 멤버십만"). 스터디 ID 순서다.
	 */
	@Transactional(readOnly = true)
	public List<MemberStudy> activeStudiesOf(long userId) {
		return this.membershipRepository.findActiveInActiveStudies(userId).stream().map(this::toMemberStudy).toList();
	}

	private MemberStudy toMemberStudy(Membership membership) {
		long studyId = membership.getStudyId();
		Study study = this.studyRepository.findById(studyId)
			.orElseThrow(() -> new IllegalStateException("멤버십의 스터디가 없어요: " + studyId));
		ZoneId zoneId = study.zoneId();
		List<StudyRule> rules = this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(studyId);
		RuleVersionCalendar calendar = RuleVersionCalendar.of(rules.stream()
			.map(rule -> new RuleVersionCalendar.Version(rule.getEffectiveFrom(), rule.toTimeRule(zoneId)))
			.toList());
		List<MemberStudy.DailyGoal> goals = rules.stream()
			.map(rule -> new MemberStudy.DailyGoal(rule.getEffectiveFrom(), rule.getDailyGoal()))
			.toList();
		List<MemberStudy.SeasonPeriod> seasons = this.seasonRepository.findByStudyIdOrderBySeqAsc(studyId)
			.stream()
			.map(season -> new MemberStudy.SeasonPeriod(season.getId(), season.getStartDay(), season.getEndDay()))
			.toList();
		return new MemberStudy(studyId, membership.getJudgedFrom(), calendar, goals, seasons);
	}

}
