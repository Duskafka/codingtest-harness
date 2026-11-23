package com.duskafka.studybot.study;

/** 기수 상태 (glossary.md, domain-rules.md 3.4 '기수'). 만들 때는 {@code SCHEDULED}다. 상태 전환은 S12. */
public enum SeasonStatus {

	SCHEDULED, ACTIVE, SETTLING, CLOSED

}
