package com.duskafka.studybot.progress.persistence;

import java.time.LocalDate;
import java.util.List;
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

}
