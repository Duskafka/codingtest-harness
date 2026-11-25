package com.duskafka.studybot.study;

import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.common.time.RuleVersionCalendar;
import com.duskafka.studybot.common.time.StudyCalendar;
import com.duskafka.studybot.study.StudyDayContext.RosterMember;
import com.duskafka.studybot.study.persistence.Membership;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.SeasonRepository;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import com.duskafka.studybot.study.persistence.StudyRule;
import com.duskafka.studybot.study.persistence.StudyRuleRepository;
import com.duskafka.studybot.study.service.StudyTimeline;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스터디 일 하루의 규칙·시각·기수·멤버를 돌려준다 ({@link StudyDayContext}). progress가 오늘 현황과 특정 날 상세를 만들 때 부른다
 * (progress → study, architecture.md 4.2, S10 만들 것 1·2). 권한 검사는 하지 않는다. 부르는 쪽이 먼저 {@link StudyAccess}로 한다.
 *
 * <p>쿼리는 스터디·규칙 버전·기수·멤버십 한 번씩이다. 멤버 수와 상관없다 (S10 수용 기준 2).
 */
@Component
@RequiredArgsConstructor
public class StudyDays {

	private final StudyRepository studyRepository;

	private final StudyRuleRepository ruleRepository;

	private final SeasonRepository seasonRepository;

	private final MembershipRepository membershipRepository;

	private final Clock clock;

	/**
	 * 스터디 일 하루의 맥락.
	 *
	 * <ul>
	 * <li>시각·목표·쉬는 요일은 그날의 규칙 버전으로 센다 (R2). 기수 안인지는 기수 행의 날짜로 본다 (5.13 '주의할 것').</li>
	 * <li>멤버는 그 스터디 일에 멤버였던 멤버십이다: 그날이 끝나기 전에 가입했고, 그날이 시작하기 전에 탈퇴하지 않았다. 탈퇴하면 그날까지
	 * 판정하므로(R14) 그날 탈퇴한 사람도 들어간다. 같은 날 탈퇴하고 다시 가입한 사람은 하나로 합치고, 멤버십 중 하나라도 그날을 판정하면
	 * 판정한다 (R14 '탈퇴한 날 다시 가입하면 그날 판정에도 들어가요').</li>
	 * </ul>
	 *
	 * @param date 스터디 일. {@code null}이면 오늘
	 */
	@Transactional(readOnly = true)
	public StudyDayContext load(long studyId, @Nullable LocalDate date) {
		Study study = this.studyRepository.findById(studyId)
			.orElseThrow(() -> new NotFoundException(StudyAccess.NOT_FOUND_DETAIL));
		List<StudyRule> rules = this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(studyId);
		RuleVersionCalendar calendar = StudyTimeline.calendar(rules, study.zoneId());
		LocalDate today = calendar.studyDayOf(this.clock.instant());
		LocalDate day = date == null ? today : date;
		Long seasonId = seasonIdOn(this.seasonRepository.findByStudyIdOrderBySeqAsc(studyId), day);
		Instant startsAt = calendar.startOf(day);
		Instant endsAt = calendar.startOf(day.plusDays(1));
		List<RosterMember> members = rosterOf(
				this.membershipRepository.findMembersDuring(studyId, startsAt, endsAt), day, seasonId != null);
		return new StudyDayContext(studyId, day, today, startsAt, endsAt, calendar.graceEndOf(day),
				StudyTimeline.ruleOn(rules, day).getDailyGoal(), StudyCalendar.of(calendar.ruleFor(day)).isRestDay(day),
				seasonId, members);
	}

	private static @Nullable Long seasonIdOn(List<Season> seasons, LocalDate day) {
		return seasons.stream().filter(season -> season.contains(day)).map(Season::getId).findFirst().orElse(null);
	}

	/** 멤버십(ID 순서)을 사용자마다 하나로 합친다. 자리는 처음 멤버십, 역할은 나중 멤버십이다. */
	private static List<RosterMember> rosterOf(List<Membership> memberships, LocalDate day, boolean inSeason) {
		Map<Long, RosterMember> byUser = new LinkedHashMap<>();
		for (Membership membership : memberships) {
			long userId = membership.getUserId();
			boolean judged = inSeason && !day.isBefore(membership.getJudgedFrom());
			RosterMember earlier = byUser.get(userId);
			byUser.put(userId, new RosterMember(userId, membership.getRole(), judged || (earlier != null && earlier.judged())));
		}
		return List.copyOf(byUser.values());
	}

}
