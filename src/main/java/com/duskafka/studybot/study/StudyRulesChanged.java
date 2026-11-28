package com.duskafka.studybot.study;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 스터디 규칙이 바뀌었다 (R2, 5.8 {@code RULE_CHANGED}, S13 만들 것 4). 규칙 변경 트랜잭션 안에서 발행한다.
 *
 * <p>study → notification 화살표가 없어서(architecture.md 4.2, 알려진 충돌 5번) study는 이 이벤트만 발행하고 알림을 모른다. 받는 쪽은
 * 같은 트랜잭션에서 알림 outbox 행을 넣는다 (사람이 정함, 2026-11-27 S13 세션 "Listener in the same transaction (Recommended)").
 * 그래서 이벤트가 사라질 틈이 없고, 규칙 변경이 롤백되면 알림도 남지 않는다. 받는 쪽이 실패하면 규칙 변경도 실패한다.
 *
 * <p>예정된 규칙이 실제로 달라졌을 때, 그리고 예정된 변경을 취소했을 때(지금 규칙과 같게 보내 예정 버전이 지워지기만 함, 사람이 정함
 * 2026-11-28 "예정된 규칙 변경을 취소만 할 때도 알리자") 발행한다. 같은 예정 변경을 다시 보내 (적용 시작일, 규칙 값)이 바꾸기 전과 모두 같거나
 * (예정 버전은 새 ID로 다시 만들어짐), 예정된 변경이 없는데 지금 규칙과 같게 보내면 발행하지 않는다.
 *
 * @param studyId 스터디 ID
 * @param studyName 스터디 이름 (알림 문구)
 * @param latestRuleId 바꾼 뒤의 마지막 규칙 버전 ID. 예정 버전은 바꿀 때마다 새 ID라 바뀐 규칙의 리비전이다 (dedupe_key). 취소면 취소된 마지막
 * 예정 버전 ID다 (버전 ID는 다시 쓰지 않으니 취소마다 다르다)
 * @param effectiveFrom 바뀐 규칙이 처음 적용되는 스터디 일 (새 버전 중 가장 이른 적용 시작일). 취소면 취소된 첫 예정 버전의 적용 시작일
 * @param cancelled 예정된 변경을 취소한 것인지
 * @param changedBy 바꾼 사람 (방장)
 * @param recipientUserIds 알릴 사람: 탈퇴하지 않은 멤버 중 바꾼 사람을 뺀 사용자 (멤버십 ID 순서)
 */
public record StudyRulesChanged(long studyId, String studyName, long latestRuleId, LocalDate effectiveFrom,
		boolean cancelled, long changedBy, List<Long> recipientUserIds) {

	public StudyRulesChanged {
		Objects.requireNonNull(studyName, "studyName");
		Objects.requireNonNull(effectiveFrom, "effectiveFrom");
		recipientUserIds = List.copyOf(recipientUserIds);
	}

}
