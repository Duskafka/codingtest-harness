package com.duskafka.studybot.judgment;

import com.duskafka.studybot.progress.JudgingState;
import com.duskafka.studybot.progress.JudgmentResult;
import com.duskafka.studybot.progress.MemberDayJudgments;
import com.duskafka.studybot.study.JudgingDay;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멤버 한 사람의 하루를 판정한다 (S12 만들 것 4, 5.10). 멤버마다 트랜잭션을 나눠서 한 멤버의 오류가 다른 멤버의 판정을 막지 않는다.
 *
 * <p>트랜잭션 안에서 멤버 일 행 하나만 잠근다 (결정 제안서 007). 문장은 셋이다: 행 만들기, 잠그기, 저장 (사람이 정함, 2026-11-27 "조회만
 * 일정, 멤버별 잠금").
 */
@Component
@RequiredArgsConstructor
class MemberJudge {

	private final MemberDayJudgments memberDays;

	private final Clock clock;

	/**
	 * @return 이번에 남긴 결과. 이미 결과가 있어 건너뛰었으면 빈 값 (다시 돌려도 안전, 5.10 '주의할 것')
	 */
	@Transactional
	public Optional<JudgmentResult> judge(JudgingDay day, long userId) {
		JudgingState state = this.memberDays.lock(day.studyId(), userId, day.studyDay(), day.seasonId());
		if (state.judged()) {
			return Optional.empty();
		}
		JudgmentResult result = JudgmentRule.decide(day.restDay(), state.solvedCount(), day.dailyGoal(),
				state.exemptionUsed());
		if (!this.memberDays.record(state.memberDayId(), result, day.ruleId(), day.seasonId(), this.clock.instant())) {
			throw new IllegalStateException("잠근 멤버 일에 판정 결과를 남기지 못했어요: " + state.memberDayId());
		}
		return Optional.of(result);
	}

}
