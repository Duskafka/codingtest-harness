package com.duskafka.studybot.study;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 스터디 일 하루의 판정을 모두 마쳐 진행 지점({@code studies.last_judged_day})을 그날로 올렸다 (5.10 처리 순서 3, S14 만들 것 5).
 * {@link JudgingDays#markJudged}가 진행 지점을 실제로 올린 트랜잭션 안에서 발행한다. 이미 올라가 있었으면 발행하지 않는다.
 *
 * <p>judgment → notification 화살표가 없어서(architecture.md 4.2, 알려진 충돌 2번) 진행 지점을 가진 study가 이 이벤트만 발행하고 알림을
 * 모른다. 받는 쪽은 같은 트랜잭션에서 결과 알림 outbox 행을 넣는다 (사람이 정함, 2026-11-28 S14 "같은 트랜잭션 리스너", 결정 제안서 010).
 * 그래서 진행 지점이 올라갔다면 알림 행도 있다. 받는 쪽이 실패하면 진행 지점도 올라가지 않고, 다음 판정 배치가 그날부터 다시 한다.
 *
 * @param studyId 스터디 ID
 * @param studyName 스터디 이름 (알림 문구)
 * @param mode 스터디 모드
 * @param ownerUserId 방장 사용자 ID ({@code studies.owner_id})
 * @param studyDay 판정을 마친 스터디 일
 * @param nextDayEndsAt 그다음 스터디 일이 끝나는 시각 ({@code startOf(studyDay + 2)}, 규칙 버전에 걸친 달력)
 */
public record StudyDayJudged(long studyId, String studyName, StudyMode mode, long ownerUserId, LocalDate studyDay,
		Instant nextDayEndsAt) {

	public StudyDayJudged {
		Objects.requireNonNull(studyName, "studyName");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(studyDay, "studyDay");
		Objects.requireNonNull(nextDayEndsAt, "nextDayEndsAt");
	}

}
