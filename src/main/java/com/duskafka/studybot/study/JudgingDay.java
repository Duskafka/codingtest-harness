package com.duskafka.studybot.study;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 판정할 스터디 일 하루 ({@link JudgingDays#nextDue}, S12 만들 것 3). 그날의 규칙 버전과 판정 대상 멤버를 한 번에 담는다.
 *
 * @param studyId 스터디 ID
 * @param studyDay 판정할 스터디 일
 * @param ruleId 그날 적용되는 규칙 버전 ID ({@code study_rules.id}). 판정 결과에 남긴다 (T11)
 * @param dailyGoal 그날 규칙의 하루 목표
 * @param restDay 그날 규칙의 쉬는 요일인지
 * @param seasonId 그날을 담은 기수 행. 기수 밖이면 {@code null}이고 판정 대상이 없다 (R9, OD-07)
 * @param targetUserIds 그날 판정하는 사용자 (R9: 가입한 다음 스터디 일부터, 탈퇴한 날까지, 기수 안). 같은 날 다시 가입한 사람은 한 번만
 * 들어 있다 (R14). 멤버십 순서다
 */
public record JudgingDay(long studyId, LocalDate studyDay, long ruleId, int dailyGoal, boolean restDay,
		@Nullable Long seasonId, List<Long> targetUserIds) {

	public JudgingDay {
		Objects.requireNonNull(studyDay, "studyDay");
		targetUserIds = List.copyOf(targetUserIds);
	}

}
