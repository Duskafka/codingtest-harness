package com.duskafka.studybot.judgment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 임시 껍데기 (S12 테스트 먼저). */
@Component
@RequiredArgsConstructor
public class JudgmentSchedule {

	private final DailyJudgmentBatch batch;

	public void runScheduled() {
		this.batch.run();
	}

}
