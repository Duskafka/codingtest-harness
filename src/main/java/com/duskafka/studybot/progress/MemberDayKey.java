package com.duskafka.studybot.progress;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 잠글 멤버 일 행 하나 (한 사용자의 행이라 사용자는 빼고 스터디와 스터디 일로 가린다).
 *
 * @param studyId 스터디 ID
 * @param studyDay 스터디 일
 * @param seasonId 행이 없어서 새로 만들 때 넣을 기수 행 ID. 기수 밖이면 {@code null}
 */
record MemberDayKey(long studyId, LocalDate studyDay, @Nullable Long seasonId) {

	/**
	 * 멤버 일 행을 잠그는 순서: 스터디 일이 이른 것부터, 같은 날이면 스터디 ID가 작은 것부터 (결정 제안서 007). 5.5의 '전날 행 → 오늘 행'을
	 * 스터디 여러 개에 걸쳐 지키는 전역 순서다. 판정(S12)은 한 트랜잭션에 한 행만 잠그니 이 순서와 부딪히지 않는다.
	 */
	static final Comparator<MemberDayKey> LOCK_ORDER = Comparator.comparing(MemberDayKey::studyDay)
		.thenComparingLong(MemberDayKey::studyId);

	MemberDayKey {
		Objects.requireNonNull(studyDay, "studyDay");
	}

	/** 잠글 행들을 잠그는 순서로 정렬하고 겹치는 행을 뺀다. */
	static List<MemberDayKey> inLockOrder(Collection<MemberDayKey> keys) {
		throw new UnsupportedOperationException("S09 구현 전");
	}

}
