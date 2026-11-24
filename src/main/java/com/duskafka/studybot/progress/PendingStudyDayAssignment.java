package com.duskafka.studybot.progress;

import org.springframework.stereotype.Component;

/**
 * S09 전까지의 배정: 아무것도 하지 않는다. S09가 스터디 일 배정으로 바꾼다 (S08 만들 것 6).
 */
@Component
class PendingStudyDayAssignment implements StudyDayAssignment {

	@Override
	public void assign(SolveToAssign solve) {
		// S09에서 채운다: 사용자가 속한 활성 스터디마다 스터디 일을 정하고 study_day_solves·member_days에 반영 (5.3 'push 처리 순서' 5).
	}

}
