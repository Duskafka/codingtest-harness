package com.duskafka.studybot.study;

import com.duskafka.studybot.common.error.ForbiddenException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스터디 권한 검사를 모으는 곳 (S05 만들 것 9, api.md 4.5 '권한 검사는 한 곳에 모으고 모든 API에 적용').
 * 스터디 ID가 경로에 있는 API는 먼저 이 검사를 거친다.
 *
 * <ul>
 * <li>멤버가 아니면(탈퇴했거나 없는 스터디) 404다. 다른 스터디가 있는지조차 드러내지 않는다 (T19).</li>
 * <li>멤버지만 방장이 아니면 403이다.</li>
 * </ul>
 *
 * 검사를 통과해도 조회 쿼리는 스터디 ID로 범위를 묶는다 (api.md 4.5 '주의할 것').
 */
@Component
@RequiredArgsConstructor
public class StudyAccess {

	static final String NOT_FOUND_DETAIL = "스터디를 찾을 수 없어요.";

	static final String OWNER_ONLY_DETAIL = "방장만 할 수 있어요.";

	private final MembershipRepository membershipRepository;

	/** 탈퇴하지 않은 멤버(방장 포함)인지 확인한다. 아니면 404. */
	@Transactional(readOnly = true)
	public StudyMember requireMember(long studyId, long userId) {
		return this.membershipRepository.findByStudyIdAndUserIdAndLeftAtIsNull(studyId, userId)
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL))
			.toStudyMember();
	}

	/**
	 * 멤버인지 확인하고 그 멤버십 행을 잠근다. 아니면 404. 잠금은 부르는 쪽 트랜잭션이 끝날 때까지 간다. 면제권은 날짜마다 멤버 일 행이 달라
	 * 그 잠금으로는 마지막 면제권 경쟁을 못 막아서, 이 사용자의 면제권 계산을 멤버십 행 잠금으로 한 줄로 세운다 (5.7, S11 만들 것 5).
	 *
	 * <p>잠금 순서: 멤버십 → 멤버 일. 스터디 행을 잠그는 흐름(결정 제안서 006)은 스터디 → 멤버십이고, 배정(결정 제안서 007)은 멤버 일만 잠근다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public StudyMember lockMember(long studyId, long userId) {
		long membershipId = this.membershipRepository.lockActive(studyId, userId)
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL));
		return this.membershipRepository.findById(membershipId)
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL))
			.toStudyMember();
	}

	/** 방장인지 확인한다. 멤버가 아니면 404, 멤버지만 방장이 아니면 403. */
	@Transactional(readOnly = true)
	public StudyMember requireOwner(long studyId, long userId) {
		StudyMember member = requireMember(studyId, userId);
		if (!member.isOwner()) {
			throw new ForbiddenException(OWNER_ONLY_DETAIL);
		}
		return member;
	}

}
