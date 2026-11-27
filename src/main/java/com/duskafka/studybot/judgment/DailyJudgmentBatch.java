package com.duskafka.studybot.judgment;

import org.springframework.stereotype.Component;

/** 임시 껍데기 (S12 테스트 먼저). */
@Component
public class DailyJudgmentBatch {

	public JudgmentSummary run() {
		return new JudgmentSummary(0, 0, 0, 0);
	}

	public JudgmentSummary judgeStudy(long studyId) {
		return new JudgmentSummary(0, 0, 0, 0);
	}

}
