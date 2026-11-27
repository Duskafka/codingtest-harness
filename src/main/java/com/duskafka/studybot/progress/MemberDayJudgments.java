package com.duskafka.studybot.progress;

import com.duskafka.studybot.progress.persistence.MemberDayRepository;
import com.duskafka.studybot.progress.persistence.MemberDayRepository.LockedMemberDay;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판정 배치가 멤버 일을 잠그고 결과를 남기는 곳 (S12 만들 것 4, 5.10). judgment가 멤버마다 한 트랜잭션 안에서 부른다
 * (judgment → progress, architecture.md 4.2). 결과를 정하는 규칙은 judgment에 있다.
 *
 * <p>한 트랜잭션에 행 하나만 잠근다 (결정 제안서 007). 배정(S09)·면제권(S11)과 같은 행을 잠가서 서로 끼어들지 않는다 (5.10 '늦게 온 풀이와
 * 경합', T8). 쓰기는 네이티브 쿼리로만 한다 ({@link MemberDayRepository} 머리말).
 */
@Component
@RequiredArgsConstructor
public class MemberDayJudgments {

	/** 판정 배치가 정한 결과의 {@code result_source} 값 (data-model '멤버 일 테이블': 값은 S12에서 정함). */
	public static final String BATCH_SOURCE = "BATCH";

	private final MemberDayRepository memberDays;

	/**
	 * 멤버 일 행을 잠근다. 없으면 만든다 (센 수 0, 미판정). 풀이도 면제권도 없던 멤버도 판정 결과를 남겨야 해서다 (3.4 '판정 배치가 행을 만듦').
	 *
	 * @param seasonId 그날을 담은 기수 행 (새로 만들 때 채움)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public JudgingState lock(long studyId, long userId, LocalDate studyDay, @Nullable Long seasonId) {
		this.memberDays.insertIfAbsent(studyId, userId, studyDay, seasonId);
		LockedMemberDay locked = this.memberDays.lock(studyId, userId, studyDay)
			.orElseThrow(() -> new IllegalStateException("만든 멤버 일 행이 없어요"));
		return new JudgingState(locked.getId(), locked.getSolvedCount(), locked.getExemptionUsed(), locked.getJudged());
	}

	/**
	 * 판정 결과를 남긴다. 결과가 이미 있으면 바꾸지 않는다. {@link #lock}으로 잠근 뒤 같은 트랜잭션에서 부른다.
	 *
	 * @param ruleId 판정에 쓴 규칙 버전 (T11)
	 * @param seasonId 그날을 담은 기수 행. 행에 비어 있으면 채운다
	 * @return 남겼는지
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean record(long memberDayId, JudgmentResult result, long ruleId, @Nullable Long seasonId,
			Instant judgedAt) {
		return this.memberDays.recordJudgment(memberDayId, result.name(), BATCH_SOURCE, judgedAt, ruleId, seasonId) == 1;
	}

}
