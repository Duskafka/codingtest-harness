package com.duskafka.studybot.study.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

	Optional<Invitation> findByTokenHash(byte[] tokenHash);

	/** 스터디 ID로 범위를 묶어 찾는다. 다른 스터디의 초대 ID로는 찾지 못한다 (api.md 4.5 IDOR). */
	Optional<Invitation> findByIdAndStudyId(long id, long studyId);

	/**
	 * 사용 횟수를 하나 늘린다. 이미 다 썼으면 바꾸지 않고 0을 돌려준다 (5.1 '정원 동시성': 영향받은 행 수로 판단).
	 * 영속성 컨텍스트의 {@link Invitation#getUsedCount()}는 갱신하지 않는다.
	 */
	@Modifying(flushAutomatically = true)
	@Query("update Invitation i set i.usedCount = i.usedCount + 1 where i.id = :id and i.usedCount < i.maxUses")
	int incrementUsedCount(@Param("id") long id);

}
