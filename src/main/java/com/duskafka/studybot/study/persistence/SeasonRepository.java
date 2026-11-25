package com.duskafka.studybot.study.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonRepository extends JpaRepository<Season, Long> {

	/** 스터디의 기수를 번호 순서로. */
	List<Season> findByStudyIdOrderBySeqAsc(long studyId);

	/**
	 * 시작 전 기수(SCHEDULED)의 끝 날짜를 바꾼다. 1기 시작 전에 기수 길이를 바꾸면 1기부터 적용한다 (사람이 정함, 2026-11-24
	 * "모두 1기 시작일부터"). 시작한 기수는 바꾸지 않는다 (R2).
	 *
	 * @return 바뀐 행 수 (0이면 이미 시작한 기수)
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "update studybot.seasons set end_day = :endDay where id = :seasonId and status = 'SCHEDULED'",
			nativeQuery = true)
	int rescheduleEnd(@Param("seasonId") long seasonId, @Param("endDay") LocalDate endDay);

}
