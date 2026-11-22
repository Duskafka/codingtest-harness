package com.duskafka.archfixtures.modules.entity.study;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 엔티티가 모듈의 루트(공개) 패키지에 있어서 걸려야 한다. */
@Entity
public class StudyEntityInRoot {

	@Id
	private Long id;

	protected StudyEntityInRoot() {
	}

}
