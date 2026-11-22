package com.duskafka.archfixtures.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** public setter가 있어 규칙에 걸린다. */
@Entity
public class PublicSetterEntity {

	@Id
	private Long id;

	private String name;

	protected PublicSetterEntity() {
	}

	public void setName(String name) {
		this.name = name;
	}

}
