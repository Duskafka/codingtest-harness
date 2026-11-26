package com.duskafka.studybot.progress;

/**
 * 현황 화면에서 멤버 하나의 그날 상태 (product.md 2.6 6번 '완료·남음·면제', S10). 판정(S12) 전에도 지금 센 수로 보여 준다.
 * 판정 결과({@link JudgmentResult})와는 따로다. 정하는 순서는 {@link MemberDayProgress#evaluate}에 있다.
 */
public enum MemberDayStatus {

	/** 목표를 채웠다 (판정하면 성공). */
	DONE,

	/** 아직 목표를 채우지 못했다 (지금 판정하면 실패). */
	REMAINING,

	/** 면제다: 쉬는 날이거나 면제권을 썼다 ({@link ExemptReason}). */
	EXEMPT,

	/** 이 멤버를 그날 판정하지 않는다 (가입한 날, 기수 밖인 날, R9 '판정 대상'). */
	NOT_JUDGED

}
