package com.duskafka.archfixtures.modules.entity.study.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 하위 패키지의 엔티티는 통과한다. */
@Entity
public class StudyRow {

	@Id
	private Long id;

	protected StudyRow() {
	}

}
