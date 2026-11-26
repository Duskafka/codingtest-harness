package com.duskafka.studybot.verification.persistence;

/**
 * 수동 인증의 상태 (glossary.md '수동 인증', 3.4 상태도, {@code self_reports.status}).
 *
 * <ul>
 * <li>{@link #ACCEPTED}: 인정됨. 그 스터디의 그날에 센 상태다 (기본 '바로 인정').</li>
 * <li>{@link #PENDING_APPROVAL}: 승인 대기 ('승인 필요' 모드). 승인·거부 API가 없어 아직 만들지 않는다 (알려진 충돌 6번).</li>
 * <li>{@link #WITHDRAWN}: 본인이 판정 전에 철회했다.</li>
 * <li>{@link #REJECTED}: 방장이 거부했거나 반대 이의가 인정됐다 (P2).</li>
 * </ul>
 */
public enum SelfReportStatus {

	ACCEPTED, PENDING_APPROVAL, WITHDRAWN, REJECTED

}
