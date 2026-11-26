package com.duskafka.studybot.verification.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 수동 인증 (S11). 스터디·사용자로 범위를 묶어 읽고 고친다 (api.md 4.5 '주의할 것', T19). */
public interface SelfReportRepository extends JpaRepository<SelfReport, Long> {

	/**
	 * 한 사람이 한 스터디의 한 스터디 일에 낸 수동 인증 수 (R7 '하루 5건'). 철회한 것도 받은 것으로 센다 (S11 PR 판단 기록). 그날 멤버 일 행을
	 * 잠근 뒤에 불러야 같은 날 동시에 낸 것과 한 줄로 선다.
	 */
	long countByStudyIdAndUserIdAndStudyDay(long studyId, long userId, LocalDate studyDay);

	/**
	 * 이 스터디에서 내가 낸 수동 인증을 잠그고 상태를 읽는다 ({@code SELECT ... FOR UPDATE}). 다른 스터디의 것, 남의 것, 없는 것이면 빈
	 * 값이다 (T19). 같은 수동 인증을 동시에 철회해도 한 번만 바뀐다.
	 */
	@Query(value = """
			select status from studybot.self_reports
			where id = :id and study_id = :studyId and user_id = :userId
			for update
			""", nativeQuery = true)
	Optional<String> lockStatus(@Param("id") long id, @Param("studyId") long studyId, @Param("userId") long userId);

	/** 철회에 필요한 값. 잠근 뒤에 읽는다. */
	@Query("""
			select new com.duskafka.studybot.verification.persistence.SelfReportRef(r.studyDay, r.solveRecordId)
			from SelfReport r where r.id = :id
			""")
	Optional<SelfReportRef> findRef(@Param("id") long id);

	/**
	 * 인정된 수동 인증을 본인 철회로 바꾼다 (3.4 '본인 철회 (판정 전)'). 인정 상태가 아니면 바꾸지 않는다.
	 *
	 * @return 바뀐 행 수
	 */
	@Modifying
	@Query(value = """
			update studybot.self_reports set status = 'WITHDRAWN', decided_by = :userId, decided_at = :decidedAt
			where id = :id and user_id = :userId and status = 'ACCEPTED'
			""", nativeQuery = true)
	int withdraw(@Param("id") long id, @Param("userId") long userId, @Param("decidedAt") Instant decidedAt);

	/**
	 * 기간 안의 스터디 일에 낸, 지금 인정된 수동 인증 수를 사용자마다 (현황의 수동 인증 비율, S11 만들 것 4). 인정된 수동 인증은 늘 그날에 센
	 * 상태다 (센 뒤에만 인정으로 남고, 철회하면 빼고 WITHDRAWN).
	 */
	@Query("""
			select new com.duskafka.studybot.verification.persistence.UserReportCount(r.userId, count(r))
			from SelfReport r
			where r.studyId = :studyId and r.studyDay between :fromDay and :toDay
				and r.status = com.duskafka.studybot.verification.persistence.SelfReportStatus.ACCEPTED
			group by r.userId
			""")
	List<UserReportCount> countAcceptedByUser(@Param("studyId") long studyId, @Param("fromDay") LocalDate fromDay,
			@Param("toDay") LocalDate toDay);

}
