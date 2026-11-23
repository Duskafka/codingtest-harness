package com.duskafka.studybot.study.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyRepository extends JpaRepository<Study, Long> {

	/**
	 * 스터디 행을 쓰기 잠금({@code SELECT ... FOR UPDATE})으로 읽는다. 트랜잭션이 끝날 때까지 같은 스터디의 수락·위임·탈퇴가
	 * 차례로 실행된다 (S05 PR 판단 기록 '정원 동시성'). 쓰기 트랜잭션 안에서 불러야 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Study s where s.id = :id")
	Optional<Study> findByIdForUpdate(@Param("id") long id);

}
