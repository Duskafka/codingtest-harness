package com.duskafka.studybot.study.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyRuleRepository extends JpaRepository<StudyRule, Long> {

	/** 스터디의 규칙 버전을 적용 시작일 순서로. */
	List<StudyRule> findByStudyIdOrderByEffectiveFromAsc(long studyId);

}
