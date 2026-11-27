package com.duskafka.studybot.judgment;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.progress.JudgmentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 멤버 일 하나의 판정 규칙 (R9, 5.10 흐름도, 3.4 '멤버 일'). */
class JudgmentRuleTest {

	@Test
	@DisplayName("쉬는 날은 풀었든 아니든 EXEMPT_REST")
	void restDayIsExemptRest() {
		assertThat(JudgmentRule.decide(true, 0, 1, false)).isEqualTo(JudgmentResult.EXEMPT_REST);
		assertThat(JudgmentRule.decide(true, 3, 1, false)).isEqualTo(JudgmentResult.EXEMPT_REST);
	}

	@Test
	@DisplayName("센 수가 목표 이상이면 SUCCESS")
	void goalMetIsSuccess() {
		assertThat(JudgmentRule.decide(false, 2, 2, false)).isEqualTo(JudgmentResult.SUCCESS);
		assertThat(JudgmentRule.decide(false, 3, 2, false)).isEqualTo(JudgmentResult.SUCCESS);
	}

	@Test
	@DisplayName("T13: 면제권을 쓴 날 목표를 채우면 SUCCESS (면제권 반환)")
	void t13GoalMetWithExemptionIsSuccess() {
		assertThat(JudgmentRule.decide(false, 1, 1, true)).isEqualTo(JudgmentResult.SUCCESS);
	}

	@Test
	@DisplayName("면제권을 쓰고 목표 미달이면 EXEMPT_TICKET")
	void exemptionWithoutGoalIsExemptTicket() {
		assertThat(JudgmentRule.decide(false, 0, 1, true)).isEqualTo(JudgmentResult.EXEMPT_TICKET);
	}

	@Test
	@DisplayName("그 밖에는 FAIL")
	void otherwiseFail() {
		assertThat(JudgmentRule.decide(false, 1, 2, false)).isEqualTo(JudgmentResult.FAIL);
	}

}
