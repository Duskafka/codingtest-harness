package com.duskafka.studybot.progress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 현황의 멤버 상태 (S10 만들 것 1, product.md 2.6 6번 '완료·남음·면제'). 판정 배치(부록 D, S12)와 같은 순서로 정하는지 본다:
 * 판정 대상 아님 → 쉬는 날 → 목표 달성 → 면제권 → 남음.
 */
class MemberDayProgressTest {

	private static MemberDayProgress evaluate(boolean judged, boolean restDay, int goal, int solved, boolean ticket) {
		return MemberDayProgress.evaluate(judged, restDay, goal, solved, ticket);
	}

	@Test
	@DisplayName("목표를 채우면 완료")
	void goalReachedIsDone() {
		assertThat(evaluate(true, false, 1, 1, false)).isEqualTo(new MemberDayProgress(MemberDayStatus.DONE, null));
		assertThat(evaluate(true, false, 2, 3, false).status()).isEqualTo(MemberDayStatus.DONE);
	}

	@Test
	@DisplayName("목표 미달이면 남음")
	void belowGoalIsRemaining() {
		assertThat(evaluate(true, false, 1, 0, false)).isEqualTo(new MemberDayProgress(MemberDayStatus.REMAINING, null));
		assertThat(evaluate(true, false, 3, 2, false).status()).isEqualTo(MemberDayStatus.REMAINING);
	}

	@Test
	@DisplayName("면제권을 쓰고 목표 미달이면 면제 (면제권)")
	void ticketBelowGoalIsExemptTicket() {
		assertThat(evaluate(true, false, 1, 0, true))
			.isEqualTo(new MemberDayProgress(MemberDayStatus.EXEMPT, ExemptReason.TICKET));
	}

	@Test
	@DisplayName("면제권을 써도 목표를 채우면 완료 (R8, 3.5 '면제권 사용 후 23:00 풀이')")
	void ticketWithGoalReachedIsDone() {
		assertThat(evaluate(true, false, 1, 1, true)).isEqualTo(new MemberDayProgress(MemberDayStatus.DONE, null));
	}

	@Test
	@DisplayName("쉬는 날은 풀었어도 면제 (쉬는 날). 판정 배치가 쉬는 날을 먼저 보기 때문이다 (부록 D)")
	void restDayComesBeforeGoal() {
		assertThat(evaluate(true, true, 1, 0, false))
			.isEqualTo(new MemberDayProgress(MemberDayStatus.EXEMPT, ExemptReason.REST_DAY));
		assertThat(evaluate(true, true, 1, 2, false))
			.isEqualTo(new MemberDayProgress(MemberDayStatus.EXEMPT, ExemptReason.REST_DAY));
		assertThat(evaluate(true, true, 1, 0, true))
			.isEqualTo(new MemberDayProgress(MemberDayStatus.EXEMPT, ExemptReason.REST_DAY));
	}

	@Test
	@DisplayName("판정 대상이 아니면 무엇이든 판정하지 않음 (가입한 날, 기수 밖, R9)")
	void notJudgedComesFirst() {
		assertThat(evaluate(false, false, 1, 1, false))
			.isEqualTo(new MemberDayProgress(MemberDayStatus.NOT_JUDGED, null));
		assertThat(evaluate(false, true, 1, 0, true))
			.isEqualTo(new MemberDayProgress(MemberDayStatus.NOT_JUDGED, null));
		assertThat(evaluate(false, false, 1, 0, false).status()).isEqualTo(MemberDayStatus.NOT_JUDGED);
	}

}
