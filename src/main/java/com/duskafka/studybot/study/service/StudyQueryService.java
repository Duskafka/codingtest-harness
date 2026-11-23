package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.study.StudyAccess;
import com.duskafka.studybot.study.StudyMember;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import com.duskafka.studybot.study.persistence.MyStudyRow;
import com.duskafka.studybot.study.persistence.SeasonRepository;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import com.duskafka.studybot.study.persistence.StudyRule;
import com.duskafka.studybot.study.persistence.StudyRuleRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 스터디 목록과 스터디 상세 (S05 만들 것 2, api.md 4.4). */
@Service
@RequiredArgsConstructor
public class StudyQueryService {

	private final StudyAccess studyAccess;

	private final StudyRepository studyRepository;

	private final StudyRuleRepository ruleRepository;

	private final SeasonRepository seasonRepository;

	private final MembershipRepository membershipRepository;

	private final Clock clock;

	/** 내가 멤버인(탈퇴하지 않은) 스터디. 최근에 가입한 것부터. */
	@Transactional(readOnly = true)
	public Page<MyStudyRow> myStudies(long userId, int page, int size) {
		return this.membershipRepository.findMyStudies(userId, PageRequest.of(page, size));
	}

	/** 스터디 상세. 멤버가 아니면 404 ({@link StudyAccess}). */
	@Transactional(readOnly = true)
	public StudyDetail detail(long studyId, long userId) {
		StudyMember me = this.studyAccess.requireMember(studyId, userId);
		Study study = this.studyRepository.findById(studyId)
			.orElseThrow(() -> new NotFoundException("스터디를 찾을 수 없어요."));
		Instant now = this.clock.instant();
		StudyRule rule = StudyTimeline.ruleAt(this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(studyId),
				study.zoneId(), now);
		var season = StudyTimeline.seasonOn(this.seasonRepository.findByStudyIdOrderBySeqAsc(studyId),
				StudyTimeline.studyDayOf(rule, study.zoneId(), now));
		long memberCount = this.membershipRepository.countByStudyIdAndLeftAtIsNull(studyId);
		return new StudyDetail(study, me.role(), memberCount, rule, season);
	}

}
