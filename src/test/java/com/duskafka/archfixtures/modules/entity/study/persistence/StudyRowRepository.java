package com.duskafka.archfixtures.modules.entity.study.persistence;

import org.springframework.data.repository.Repository;

/** 하위 패키지의 저장소는 통과한다. */
public interface StudyRowRepository extends Repository<StudyRow, Long> {
}
