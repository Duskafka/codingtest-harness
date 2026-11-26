package com.duskafka.studybot.progress.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyDaySolveRepository extends JpaRepository<StudyDaySolve, Long> {

	/**
	 * 스터디 하루에 센 풀이 중 최근에 배정한 것부터 {@code limit}개 (S10 현황의 '최근 풀이'). 조회 전용 DTO로 읽는다. 인덱스
	 * {@code study_day_solves_study_day_ix}를 쓴다.
	 */
	@Query("""
			select new com.duskafka.studybot.progress.persistence.CountedSolveRow(s.solveRecordId, s.userId, s.platform,
				s.problemNo)
			from StudyDaySolve s
			where s.studyId = :studyId and s.studyDay = :studyDay
			order by s.id desc
			""")
	List<CountedSolveRow> findLatest(@Param("studyId") long studyId, @Param("studyDay") LocalDate studyDay, Limit limit);

	/**
	 * 풀이 하나를 그 스터디 일에 센다. 같은 날 같은 문제가 이미 있으면 아무것도 하지 않는다 (R5, T4·T5). 멤버 일 행을 잠근 트랜잭션
	 * 안에서 부른다 (5.5).
	 *
	 * @return 넣은 행 수 (1: 새로 셈, 0: 같은 날 같은 문제가 이미 있음)
	 */
	@Modifying
	@Query(value = """
			insert into studybot.study_day_solves (study_id, user_id, study_day, solve_record_id, platform, problem_no)
			values (:studyId, :userId, :studyDay, :solveRecordId, :platform, :problemNo)
			on conflict (study_id, user_id, study_day, platform, problem_no) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("studyId") long studyId, @Param("userId") long userId,
			@Param("studyDay") LocalDate studyDay, @Param("solveRecordId") long solveRecordId,
			@Param("platform") String platform, @Param("problemNo") String problemNo);

	/**
	 * 그 날에 센 풀이 기록 하나를 뺀다 (수동 인증 철회, S11 만들 것 3). 같은 날 같은 문제를 다른 풀이 기록이 세고 있으면 그 행은 그대로다.
	 * 멤버 일 행을 잠근 트랜잭션 안에서 부른다.
	 *
	 * @return 지운 행 수
	 */
	@Modifying
	@Query(value = """
			delete from studybot.study_day_solves
			where study_id = :studyId and user_id = :userId and study_day = :studyDay and solve_record_id = :solveRecordId
			""", nativeQuery = true)
	int deleteSolve(@Param("studyId") long studyId, @Param("userId") long userId, @Param("studyDay") LocalDate studyDay,
			@Param("solveRecordId") long solveRecordId);

	/** 그 날에 이 풀이 기록으로 센 행 (수동 인증 철회, S11). 없으면 빈 값. 조회 전용 DTO로 읽는다. */
	@Query("""
			select new com.duskafka.studybot.progress.persistence.CountedSolveRow(s.solveRecordId, s.userId, s.platform,
				s.problemNo)
			from StudyDaySolve s
			where s.studyId = :studyId and s.userId = :userId and s.studyDay = :studyDay
				and s.solveRecordId = :solveRecordId
			""")
	Optional<CountedSolveRow> findCounted(@Param("studyId") long studyId, @Param("userId") long userId,
			@Param("studyDay") LocalDate studyDay, @Param("solveRecordId") long solveRecordId);

	/**
	 * 주어진 풀이 기록(그 사용자의 것) 중 이 스터디에서 어느 날에든 이미 센 것의 ID (풀이 하나를 두 날에 세지 않게, S11). 사용자로도 묶어
	 * UNIQUE {@code (study_id, user_id, ...)}의 앞부분을 쓴다.
	 */
	@Query("""
			select s.solveRecordId from StudyDaySolve s
			where s.studyId = :studyId and s.userId = :userId and s.solveRecordId in :solveRecordIds
			""")
	List<Long> findCountedAmong(@Param("studyId") long studyId, @Param("userId") long userId,
			@Param("solveRecordIds") Collection<Long> solveRecordIds);

	/**
	 * 그 날에 센 행의 풀이 기록을 다른 것으로 바꾼다 (수동 인증 철회 때 같은 문제의 다른 풀이가 대신 셈, S11, 사람이 정함 2026-11-26 "다른 풀이로
	 * 바꿔 끼움"). 같은 문제라 UNIQUE(R5)와 센 수는 그대로다. 멤버 일 행을 잠근 트랜잭션 안에서 부른다.
	 *
	 * @return 바뀐 행 수
	 */
	@Modifying
	@Query(value = """
			update studybot.study_day_solves set solve_record_id = :replacementId
			where study_id = :studyId and user_id = :userId and study_day = :studyDay and solve_record_id = :solveRecordId
			""", nativeQuery = true)
	int replaceSolve(@Param("studyId") long studyId, @Param("userId") long userId, @Param("studyDay") LocalDate studyDay,
			@Param("solveRecordId") long solveRecordId, @Param("replacementId") long replacementId);

	/** 기간 안에 그 스터디에 센 풀이 수를 멤버마다 (현황의 수동 인증 비율의 분모, S11 만들 것 4). 센 풀이가 없는 멤버는 빠진다. */
	@Query(value = """
			select user_id as "userId", count(*) as "count" from studybot.study_day_solves
			where study_id = :studyId and study_day between :fromDay and :toDay
			group by user_id
			""", nativeQuery = true)
	List<MemberDayRepository.UserCount> countByUser(@Param("studyId") long studyId, @Param("fromDay") LocalDate fromDay,
			@Param("toDay") LocalDate toDay);

}
