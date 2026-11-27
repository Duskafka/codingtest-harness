package com.duskafka.studybot.study.persistence;

import java.time.Instant;
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

	/**
	 * 아직 시작하지 않은 다음 기수 행의 끝 날짜를 바꾼다 (S12 만들 것 6 '예정 행의 끝 날짜', R2, 5.13). 기수 중 규칙 변경으로 다음 기수 시작일에
	 * 적용되는 기수 길이가 달라지면(변경·취소) 부른다. 시작했는지는 상태가 아니라 날짜로 본다 (5.13 '주의할 것'): 시작일이 오늘보다 뒤인 행만
	 * 바꾼다. 다음 기수 행이 아직 없으면(판정 배치가 돌기 전) 아무것도 바꾸지 않고, 배치가 만들 때 바뀐 규칙의 길이를 쓴다.
	 *
	 * @param startDay 다음 기수 시작일
	 * @param today 규칙 버전을 반영해 센 오늘(스터디 일)
	 * @param endDay 새 끝 날짜
	 * @return 바뀐 행 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update studybot.seasons set end_day = :endDay
			where study_id = :studyId and start_day = :startDay and start_day > :today
			""", nativeQuery = true)
	int rescheduleUpcoming(@Param("studyId") long studyId, @Param("startDay") LocalDate startDay,
			@Param("today") LocalDate today, @Param("endDay") LocalDate endDay);

	/**
	 * 마지막 날까지 판정한 진행 기수를 정산 중으로 바꾸고 그 시각을 남긴다 (3.4 'ACTIVE → SETTLING: 마지막 날 판정 완료', R13, S12). 판정 배치가
	 * 하루치 판정을 마치고 진행 지점을 올릴 때 부른다.
	 *
	 * @param judgedDay 방금 판정을 마친 스터디 일 (진행 지점)
	 * @return 바뀐 행 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update studybot.seasons set status = 'SETTLING', settling_started_at = :now
			where study_id = :studyId and status = 'ACTIVE' and end_day <= :judgedDay
			""", nativeQuery = true)
	int markSettling(@Param("studyId") long studyId, @Param("judgedDay") LocalDate judgedDay, @Param("now") Instant now);

}
