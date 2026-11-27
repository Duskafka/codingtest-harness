package com.duskafka.studybot.judgment;

import com.duskafka.studybot.progress.JudgmentResult;

/** 임시 껍데기 (S12 테스트 먼저). */
final class JudgmentRule {

	private JudgmentRule() {
	}

	static JudgmentResult decide(boolean restDay, int solvedCount, int dailyGoal, boolean exemptionUsed) {
		return JudgmentResult.FAIL;
	}

}
