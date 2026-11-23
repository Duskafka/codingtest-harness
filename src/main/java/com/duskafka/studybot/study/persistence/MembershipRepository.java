package com.duskafka.studybot.study.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

	/** 스터디의 탈퇴하지 않은 멤버십. 없으면 그 사람은 멤버가 아니다. */
	Optional<Membership> findByStudyIdAndUserIdAndLeftAtIsNull(long studyId, long userId);

	/** 스터디의 탈퇴하지 않은 멤버 수 (정원 검사, 5.1). */
	long countByStudyIdAndLeftAtIsNull(long studyId);

	/**
	 * 내 스터디 목록 (api.md 4.4 {@code GET /api/studies}). 탈퇴하지 않은 멤버십만, 최근에 가입한 것부터.
	 * 정렬은 쿼리에 고정한다. 요청의 정렬 값을 쿼리에 넣지 않는다.
	 */
	@Query(value = """
			select new com.duskafka.studybot.study.persistence.MyStudyRow(s.id, s.name, s.mode, m.role)
			from Membership m join Study s on s.id = m.studyId
			where m.userId = :userId and m.leftAt is null
			order by m.joinedAt desc, m.id desc
			""", countQuery = """
			select count(m) from Membership m where m.userId = :userId and m.leftAt is null
			""")
	Page<MyStudyRow> findMyStudies(@Param("userId") long userId, Pageable pageable);

}
