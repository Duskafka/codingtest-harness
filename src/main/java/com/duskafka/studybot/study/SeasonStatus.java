package com.duskafka.studybot.study;

/**
 * 기수 상태 (glossary.md, domain-rules.md 3.4 '기수'). 만들 때는 {@code SCHEDULED}다. 판정 배치(S12)가 시작일에 {@code ACTIVE}로,
 * 마지막 날 판정이 끝나면 {@code SETTLING}으로, 그 뒤 이의 기간(72시간)이 지나면 {@code CLOSED}로 바꾼다. 상태는 배치가 돌 때 바뀌어
 * 늦을 수 있으니, 기수가 시작했는지·그날이 기수 안인지는 날짜로 본다 (5.13 '주의할 것').
 */
public enum SeasonStatus {

	SCHEDULED, ACTIVE, SETTLING, CLOSED

}
