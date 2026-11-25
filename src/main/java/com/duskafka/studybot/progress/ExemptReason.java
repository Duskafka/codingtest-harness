package com.duskafka.studybot.progress;

/** 면제인 까닭 ({@link MemberDayStatus#EXEMPT}). 판정 결과의 {@code EXEMPT_REST}·{@code EXEMPT_TICKET}과 같은 구분이다 (R9). */
public enum ExemptReason {

	/** 그날 규칙의 쉬는 요일. */
	REST_DAY,

	/** 면제권을 썼고 목표를 채우지 못했다 (R8). */
	TICKET

}
