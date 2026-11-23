package com.duskafka.studybot.study.service;

import com.duskafka.studybot.study.StudyMode;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 새 스터디 (5.1 처리 순서 1).
 *
 * @param name 이름 (앞뒤 공백을 뺀 값)
 * @param mode 모드
 * @param timezone 스터디 시간대 ID. 만든 뒤에는 바꾸지 않는다 (5.1)
 * @param startDate 시작일. 없으면 다음 스터디 일
 * @param rule 첫 규칙 버전
 */
public record NewStudy(String name, StudyMode mode, String timezone, @Nullable LocalDate startDate, RuleSettings rule) {

	/** 시간대 기본값 (domain-rules.md 3.1, D7). */
	public static final String DEFAULT_TIMEZONE = "Asia/Seoul";

	public NewStudy {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(timezone, "timezone");
		Objects.requireNonNull(rule, "rule");
	}

}
