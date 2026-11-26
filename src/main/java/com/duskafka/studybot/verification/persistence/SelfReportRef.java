package com.duskafka.studybot.verification.persistence;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * 철회에 필요한 수동 인증의 값 (조회 전용 DTO). 엔티티를 영속성 컨텍스트에 올리지 않고 읽는다.
 *
 * @param studyDay 인증한 스터디 일
 * @param solveRecordId 이 수동 인증으로 만든 풀이 기록. 인정된 수동 인증에는 늘 있다
 */
public record SelfReportRef(LocalDate studyDay, @Nullable Long solveRecordId) {
}
