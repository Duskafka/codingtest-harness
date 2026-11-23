package com.duskafka.studybot.study;

/**
 * 기수 상태 (glossary.md, domain-rules.md 3.4 '기수'). 만들 때는 {@code SCHEDULED}다.
 * 마지막 날 판정 뒤의 전환(ACTIVE → SETTLING → CLOSED)은 S12다. 시작일의 SCHEDULED → ACTIVE 전환을 맡은 단계는 아직 없다
 * (S05 PR 열린 질문).
 */
public enum SeasonStatus {

	SCHEDULED, ACTIVE, SETTLING, CLOSED

}
