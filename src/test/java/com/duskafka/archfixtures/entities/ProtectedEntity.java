package com.duskafka.archfixtures.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 규칙을 지키는 엔티티: protected 기본 생성자, public setter 없음. */
@Entity
public class ProtectedEntity {

	@Id
	private Long id;

	private String name;

	protected ProtectedEntity() {
	}

	public ProtectedEntity(String name) {
		this.name = name;
	}

	public void rename(String newName) {
		this.name = newName;
	}

}
