package com.duskafka.studybot.study.web;

import com.duskafka.studybot.study.StudyMode;
import com.duskafka.studybot.study.service.NewStudy;
import com.duskafka.studybot.study.service.StudyLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /api/studies} 요청 (api.md 4.4 '스터디 만들기 (규칙, 첫 기수 포함)', product.md 2.4 화면 3).
 *
 * @param name 이름. 앞뒤 공백을 빼고 1~50자
 * @param mode 모드 (GROUP / SOLO)
 * @param timezone 스터디 시간대 ID. 없으면 Asia/Seoul. 만든 뒤에는 바꾸지 않는다
 * @param startDate 시작일. 없으면 다음 스터디 일. 다음 스터디 일 ~ 오늘 + 30일
 * @param rule 규칙. 없거나 빠진 항목은 R1 기본값
 */
record CreateStudyRequest(@Nullable @NotNull @Size(min = 1, max = StudyLimits.NAME_MAX_LENGTH) String name,
		@Nullable @NotNull StudyMode mode, @Nullable String timezone, @Nullable LocalDate startDate,
		@Nullable @Valid RuleRequest rule) {

	CreateStudyRequest {
		name = name != null ? name.strip() : null;
	}

	/** 검증을 통과한 뒤에만 부른다 (name, mode가 있음). */
	NewStudy toNewStudy() {
		return new NewStudy(requireNonNull(this.name), requireNonNull(this.mode),
				this.timezone != null ? this.timezone : NewStudy.DEFAULT_TIMEZONE, this.startDate,
				(this.rule != null ? this.rule : RuleRequest.EMPTY).toSettings());
	}

	private static <T> T requireNonNull(@Nullable T value) {
		if (value == null) {
			throw new IllegalStateException("검증을 통과하지 않은 요청이에요");
		}
		return value;
	}

}
