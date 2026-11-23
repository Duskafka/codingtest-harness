package com.duskafka.studybot.study;

/**
 * 수동 인증 방식 (R1, R7: 바로 인정 / 승인 필요, D19). glossary에 없는 이름이라 S05 PR 판단 기록에 남겼다.
 * '승인 필요'의 승인·거부 API는 아직 없다 (architecture.md 알려진 충돌 6번, S11).
 */
public enum SelfReportMode {

	IMMEDIATE, APPROVAL_REQUIRED

}
