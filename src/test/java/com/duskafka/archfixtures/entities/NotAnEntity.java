package com.duskafka.archfixtures.entities;

/** 엔티티가 아니라서 setter가 있어도 규칙 대상이 아니다. */
public class NotAnEntity {

	private String name;

	public void setName(String name) {
		this.name = name;
	}

}
