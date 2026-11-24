package com.duskafka.studybot.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** 뼈대: S05 규칙 변경 구현 전. */
public final class RuleVersionCalendar {

	/**
	 * 규칙 버전 하나의 시간 규칙.
	 *
	 * @param effectiveFrom 이 버전을 적용하기 시작하는 스터디 일
	 * @param rule 시간 규칙
	 */
	public record Version(LocalDate effectiveFrom, StudyTimeRule rule) {

		public Version {
			Objects.requireNonNull(effectiveFrom, "effectiveFrom");
			Objects.requireNonNull(rule, "rule");
		}

	}

	private RuleVersionCalendar() {
	}

	public static RuleVersionCalendar of(List<Version> versions) {
		throw new UnsupportedOperationException("S05 구현 전");
	}

	public StudyTimeRule ruleFor(LocalDate studyDay) {
		throw new UnsupportedOperationException("S05 구현 전");
	}

	public Instant startOf(LocalDate studyDay) {
		throw new UnsupportedOperationException("S05 구현 전");
	}

	public LocalDate studyDayOf(Instant instant) {
		throw new UnsupportedOperationException("S05 구현 전");
	}

	public LocalDate nextStudyDayOf(Instant instant) {
		throw new UnsupportedOperationException("S05 구현 전");
	}

}
