package com.duskafka.studybot.progress;

/**
 * 잠근 멤버 일 행의 지금 상태 중 배정에 필요한 것 (R4).
 *
 * @param judged 판정 결과가 있는지 ({@code result}가 있으면 판정함)
 * @param exemptionUsed 면제권을 썼는지 ({@code exemption_used_at})
 * @param solvedCount 센 문제 수 ({@code solved_count})
 */
record MemberDayState(boolean judged, boolean exemptionUsed, int solvedCount) {

}
