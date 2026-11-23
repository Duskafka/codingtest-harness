package com.duskafka.studybot.study;

import java.time.LocalDate;

/**
 * 스터디의 (탈퇴하지 않은) 멤버. {@link StudyAccess}가 권한을 확인하고 돌려준다.
 *
 * @param studyId 스터디 ID
 * @param userId 사용자 ID
 * @param role 역할
 * @param judgedFrom 판정을 시작하는 스터디 일 (R14)
 */
public record StudyMember(long studyId, long userId, MembershipRole role, LocalDate judgedFrom) {

	public boolean isOwner() {
		return this.role == MembershipRole.OWNER;
	}

}
