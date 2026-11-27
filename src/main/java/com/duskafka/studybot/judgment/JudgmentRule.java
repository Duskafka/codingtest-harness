package com.duskafka.studybot.judgment;

import com.duskafka.studybot.progress.JudgmentResult;

/**
 * 멤버 일 하나의 판정 규칙 (R9, R8, 5.10 흐름도, 3.4 '멤버 일'). 순서대로 본다.
 *
 * <ol>
 * <li>쉬는 날 → {@code EXEMPT_REST}. 풀었어도 면제다 (5.10 흐름도 '쉬는 날?'이 먼저).</li>
 * <li>센 수 ≥ 목표 → {@code SUCCESS}. 면제권을 쓴 날이면 면제권이 돌아온다 (R8, T13). 남은 면제권은 성공으로 판정된 날을 세지 않으니
 * ({@code MemberDayRepository#countUsedExemptions}) 결과만 남기면 된다.</li>
 * <li>면제권 사용 → {@code EXEMPT_TICKET}.</li>
 * <li>그 밖에는 {@code FAIL}. 원장 CHARGE는 P2다.</li>
 * </ol>
 */
final class JudgmentRule {

	private JudgmentRule() {
	}

	/**
	 * @param restDay 그날 규칙의 쉬는 요일인지
	 * @param solvedCount 그날 센 문제 수
	 * @param dailyGoal 그날 규칙의 하루 목표
	 * @param exemptionUsed 그날 면제권을 썼는지
	 */
	static JudgmentResult decide(boolean restDay, int solvedCount, int dailyGoal, boolean exemptionUsed) {
		if (restDay) {
			return JudgmentResult.EXEMPT_REST;
		}
		if (solvedCount >= dailyGoal) {
			return JudgmentResult.SUCCESS;
		}
		if (exemptionUsed) {
			return JudgmentResult.EXEMPT_TICKET;
		}
		return JudgmentResult.FAIL;
	}

}
