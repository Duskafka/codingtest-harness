package com.duskafka.archfixtures.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 기본 생성자가 public이라 규칙에 걸린다. */
@Entity
public class PublicNoArgConstructorEntity {

	@Id
	private Long id;

	public PublicNoArgConstructorEntity() {
	}

}
