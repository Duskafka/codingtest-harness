package com.duskafka.archfixtures.modules.entity.progress;

import com.duskafka.archfixtures.modules.entity.study.persistence.StudyRow;
import org.springframework.data.repository.Repository;

/** 저장소가 모듈의 루트(공개) 패키지에 있어서 걸려야 한다. */
public interface ProgressRepositoryInRoot extends Repository<StudyRow, Long> {
}
