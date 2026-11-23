package com.duskafka.studybot.study.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

}
