package com.duskafka.studybot.progress.persistence;

/**
 * 현황에 쓰는 스터디별 집계 한 행 (조회 전용 DTO 쿼리, OD-04 기본안, S10).
 *
 * @param solveRecordId 그날 센 풀이 기록
 * @param userId 사용자 ID
 * @param platform 플랫폼 이름
 * @param problemNo 문제 번호
 */
public record CountedSolveRow(long solveRecordId, long userId, String platform, String problemNo) {

}
