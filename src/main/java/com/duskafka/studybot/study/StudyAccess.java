package com.duskafka.studybot.study;

import com.duskafka.studybot.common.error.ForbiddenException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
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
