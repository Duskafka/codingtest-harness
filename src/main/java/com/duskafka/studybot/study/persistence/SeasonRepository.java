package com.duskafka.studybot.study.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonRepository extends JpaRepository<Season, Long> {

	/** 스터디의 기수를 번호 순서로. */
	List<Season> findByStudyIdOrderBySeqAsc(long studyId);

}
