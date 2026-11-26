package com.duskafka.studybot.verification.persistence;

/**
 * 사용자 한 사람의 수동 인증 수 (조회 전용 DTO).
 *
 * @param userId 사용자 ID
 * @param count 수
 */
public record UserReportCount(long userId, long count) {
}
