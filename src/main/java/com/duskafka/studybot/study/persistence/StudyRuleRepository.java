package com.duskafka.studybot.study.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyRuleRepository extends JpaRepository<StudyRule, Long> {

	/** 스터디의 규칙 버전을 적용 시작일 순서로. */
	List<StudyRule> findByStudyIdOrderByEffectiveFromAsc(long studyId);

	/**
	 * 그날부터 적용할 예정이던 버전을 지운다 (규칙을 다시 바꾸면 예정 버전을 새로 만든다, S05). 이미 적용된 버전은 지우지 않도록
	 * 부르는 쪽이 {@code from}을 다음 스터디 일 이후로 준다 (R2 소급 없음). 앞서 바꾼 엔티티를 먼저 반영하고, 지운 행이 요청 내내 열린
	 * EntityManager(open-in-view)에 남지 않게 비운다.
	 *
	 * @return 지운 버전 수
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "delete from studybot.study_rules where study_id = :studyId and effective_from >= :from",
			nativeQuery = true)
	int deleteFrom(@Param("studyId") long studyId, @Param("from") LocalDate from);

}
