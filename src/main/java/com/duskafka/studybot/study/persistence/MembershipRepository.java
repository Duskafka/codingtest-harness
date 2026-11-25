package com.duskafka.studybot.study.persistence;

import java.time.Instant;
import java.util.List;
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

	/**
	 * 스터디 일 하나 동안 멤버였던 멤버십, ID 순서 (S10 현황). 그날이 끝나기 전에 가입했고({@code joined_at < endsAt}) 그날이 시작하기 전에
	 * 탈퇴하지 않은({@code left_at >= startsAt}) 것이다. 탈퇴한 시각이 속한 스터디 일까지 판정하기 때문이다 (R14).
	 *
	 * @param startsAt 그 스터디 일이 시작하는 시각
	 * @param endsAt 다음 스터디 일이 시작하는 시각
	 */
	@Query("""
			select m from Membership m
			where m.studyId = :studyId and m.joinedAt < :endsAt and (m.leftAt is null or m.leftAt >= :startsAt)
			order by m.id
			""")
	List<Membership> findMembersDuring(@Param("studyId") long studyId, @Param("startsAt") Instant startsAt,
			@Param("endsAt") Instant endsAt);

	/**
	 * 사용자의 탈퇴하지 않은 멤버십 중 활성 스터디의 것, 스터디 ID 순서 (풀이 배정, S09). 스터디마다 탈퇴하지 않은 멤버십은 하나다
	 * (부분 UNIQUE).
	 */
	@Query("""
			select m from Membership m join Study s on s.id = m.studyId
			where m.userId = :userId and m.leftAt is null and s.status = com.duskafka.studybot.study.persistence.StudyStatus.ACTIVE
			order by m.studyId
			""")
	List<Membership> findActiveInActiveStudies(@Param("userId") long userId);

}
