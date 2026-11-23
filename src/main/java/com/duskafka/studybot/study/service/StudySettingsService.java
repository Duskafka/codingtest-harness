package com.duskafka.studybot.study.service;

import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.study.StudyAccess;
import com.duskafka.studybot.study.persistence.Membership;
import com.duskafka.studybot.study.persistence.MembershipRepository;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이름 변경과 방장 위임 (S05 만들 것 3, api.md 4.4 {@code PATCH /api/studies/{id}}, 권한: 방장). */
@Service
@RequiredArgsConstructor
public class StudySettingsService {

	private final StudyAccess studyAccess;

	private final StudyRepository studyRepository;

	private final MembershipRepository membershipRepository;

	/**
	 * 이름을 바꾸거나 방장을 위임한다. 둘 다 없으면 400.
	 *
	 * <p>스터디 행을 잠근 뒤 권한을 본다 (결정 제안서 006). 같은 스터디의 수락·위임·탈퇴가 차례로 실행돼서, 위임하는 사이에 상대가 탈퇴하는 일이 없다.
	 * 위임은 옛 방장을 멤버로 내린 뒤 새 방장을 올린다 (탈퇴하지 않은 방장은 스터디마다 하나, DB 부분 UNIQUE).
	 *
	 * @param newName 새 이름 (앞뒤 공백을 뺀 값). 바꾸지 않으면 null
	 * @param newOwnerUserId 새 방장의 사용자 ID. 위임하지 않으면 null. 나 자신이면 바꾸지 않는다
	 */
	@Transactional
	public void update(long studyId, long actorUserId, @Nullable String newName, @Nullable Long newOwnerUserId) {
		Study study = this.studyRepository.findByIdForUpdate(studyId)
			.orElseThrow(() -> new NotFoundException("스터디를 찾을 수 없어요."));
		this.studyAccess.requireOwner(studyId, actorUserId);
		// 권한을 먼저 본다. 다른 스터디 ID에 빈 본문을 보내도 400이 아니라 404다 (T19).
		if (newName == null && newOwnerUserId == null) {
			throw new BadRequestException("바꿀 값(name 또는 ownerUserId)이 없어요.");
		}
		if (newName != null) {
			study.rename(newName);
		}
		if (newOwnerUserId != null && newOwnerUserId != actorUserId) {
			delegate(study, actorUserId, newOwnerUserId);
		}
	}

	private void delegate(Study study, long actorUserId, long newOwnerUserId) {
		long studyId = study.getId();
		Membership target = this.membershipRepository.findByStudyIdAndUserIdAndLeftAtIsNull(studyId, newOwnerUserId)
			.orElseThrow(() -> new BadRequestException("위임할 사람이 이 스터디의 멤버가 아니에요."));
		Membership actor = this.membershipRepository.findByStudyIdAndUserIdAndLeftAtIsNull(studyId, actorUserId)
			.orElseThrow(() -> new NotFoundException("스터디를 찾을 수 없어요."));
		actor.demoteToMember();
		this.membershipRepository.flush();
		target.promoteToOwner();
		study.changeOwner(newOwnerUserId);
	}

}
