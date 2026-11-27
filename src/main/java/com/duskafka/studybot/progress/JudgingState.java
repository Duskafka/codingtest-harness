package com.duskafka.studybot.progress;

/**
 * 판정하려고 잠근 멤버 일 하나의 지금 값 ({@link MemberDayJudgments#lock}, S12).
 *
 * @param memberDayId 멤버 일 행 ID
 * @param solvedCount 센 문제 수
 * @param exemptionUsed 면제권을 썼는지
 * @param judged 이미 판정 결과가 있는지
 */
public record JudgingState(long memberDayId, int solvedCount, boolean exemptionUsed, boolean judged) {

}
