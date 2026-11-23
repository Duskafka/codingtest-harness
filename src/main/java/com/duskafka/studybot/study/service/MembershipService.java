package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.study.StudyAccess;
import com.duskafka.studybot.study.StudyMember;
import com.duskafka.studybot.study.persistence.Membership;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import com.duskafka.studybot.study.persistence.StudyRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 스터디 탈퇴 (S05 만들 것 7, R14, 5.16 '스터디 탈퇴'). */
@Service
@RequiredArgsConstructor
public class MembershipService {

	private final StudyAccess studyAccess;

	private final StudyRepository studyRepository;

	private final MembershipRepository membershipRepository;

	private final Clock clock;

	/**
	 * 탈퇴한다. {@code left_at}을 남기고 그 시각이 속한 스터디 일까지 판정한다 (R14). 원장(P2)은 지우지 않는다.
	 * 방장은 위임한 뒤에만 탈퇴할 수 있다 (409). 스터디 행을 잠가 위임과 차례로 실행한다.
	 */
	@Transactional
	public void leave(long studyId, long userId) {
		this.studyRepository.findByIdForUpdate(studyId)
			.orElseThrow(() -> new NotFoundException("스터디를 찾을 수 없어요."));
		StudyMember me = this.studyAccess.requireMember(studyId, userId);
		if (me.isOwner()) {
			throw new ConflictException("방장은 다른 멤버에게 방장을 넘긴 뒤에 탈퇴할 수 있어요.");
		}
		Membership membership = this.membershipRepository.findByStudyIdAndUserIdAndLeftAtIsNull(studyId, userId)
			.orElseThrow(() -> new NotFoundException("스터디를 찾을 수 없어요."));
		membership.leave(this.clock.instant());
	}

}
