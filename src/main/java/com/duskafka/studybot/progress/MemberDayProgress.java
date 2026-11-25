package com.duskafka.studybot.progress;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 멤버 하나의 그날 상태와 면제 까닭 (S10 현황). 순수 계산이다.
 *
 * @param status 상태
 * @param exemptReason {@link MemberDayStatus#EXEMPT}일 때만 있다
 */
public record MemberDayProgress(MemberDayStatus status, @Nullable ExemptReason exemptReason) {

	public MemberDayProgress {
		Objects.requireNonNull(status, "status");
	}

	/**
	 * 그날 상태를 정한다. 판정 배치(부록 D, S12)와 같은 순서다: 쉬는 날 → 목표 달성 → 면제권 → 그 밖. 면제권을 썼어도 목표를 채우면
	 * 완료다 (R8). 판정 대상이 아니면 무엇보다 먼저 {@link MemberDayStatus#NOT_JUDGED}다 (R9).
	 *
	 * @param judged 이 멤버를 그날 판정하는지 (R9 '판정 대상')
	 * @param restDay 그날 규칙의 쉬는 요일인지
	 * @param dailyGoal 그날 규칙의 하루 목표
	 * @param solvedCount 그날 센 문제 수
	 * @param exemptionUsed 그날 면제권을 썼는지
	 */
	public static MemberDayProgress evaluate(boolean judged, boolean restDay, int dailyGoal, int solvedCount,
			boolean exemptionUsed) {
		if (!judged) {
			return new MemberDayProgress(MemberDayStatus.NOT_JUDGED, null);
		}
		if (restDay) {
			return new MemberDayProgress(MemberDayStatus.EXEMPT, ExemptReason.REST_DAY);
		}
		if (solvedCount >= dailyGoal) {
			return new MemberDayProgress(MemberDayStatus.DONE, null);
		}
		if (exemptionUsed) {
			return new MemberDayProgress(MemberDayStatus.EXEMPT, ExemptReason.TICKET);
		}
		return new MemberDayProgress(MemberDayStatus.REMAINING, null);
	}

}
