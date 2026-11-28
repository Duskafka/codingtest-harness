package com.duskafka.studybot.study.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyRepository extends JpaRepository<Study, Long> {

	/**
	 * 스터디 행을 쓰기 잠금으로 읽는다. Hibernate 7.4의 PostgreSQL 방언은 {@code SELECT ... FOR NO KEY UPDATE}를 낸다.
	 * 이 잠금끼리는 서로 기다리므로 트랜잭션이 끝날 때까지 같은 스터디의 수락·위임·탈퇴가 차례로 실행된다
	 * (결정 제안서 006). 다른 테이블이 이 행을 FK로 참조하는 것(KEY SHARE)은 막지 않는다. 쓰기 트랜잭션 안에서 불러야 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Study s where s.id = :id")
	Optional<Study> findByIdForUpdate(@Param("id") long id);

	/** 활성 스터디의 ID, ID 순서 (판정 배치, S12). */
	@Query("select s.id from Study s where s.status = com.duskafka.studybot.study.persistence.StudyStatus.ACTIVE order by s.id")
	List<Long> findActiveIds();

	/**
	 * 판정 배치의 진행 지점을 그 스터디 일로 올린다 (5.10 처리 순서 3, S12 만들 것 5). 이미 그날이나 그 뒤면 바꾸지 않는다. 엔티티를 거치지 않는다.
	 *
	 * @return 바뀐 행 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update studybot.studies set last_judged_day = :day
			where id = :id and (last_judged_day is null or last_judged_day < :day)
			""", nativeQuery = true)
	int advanceLastJudgedDay(@Param("id") long id, @Param("day") LocalDate day);

	/**
	 * 마감 전 알림 생성의 진행 지점을 그 스터디 일로 올린다 (5.8, pseudocode {@code study.lastRemindedDay = day}, S13 만들 것 2). 이미 그날이나
	 * 그 뒤면 바꾸지 않는다. 엔티티를 거치지 않는다.
	 *
	 * @return 바뀐 행 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			update studybot.studies set last_reminded_day = :day
			where id = :id and (last_reminded_day is null or last_reminded_day < :day)
			""", nativeQuery = true)
	int advanceLastRemindedDay(@Param("id") long id, @Param("day") LocalDate day);

}
