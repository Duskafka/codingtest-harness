package com.duskafka.studybot.study;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 멤버 한 사람이 속한 스터디 하나의 배정 값({@link MemberStudy})과, 수동 인증·면제권에 필요한 규칙 항목 (S11). {@link MemberStudies#rulesOf}가
 * 만든다.
 *
 * @param study 배정에 쓰는 값 (달력, 하루 목표, 기수, 판정 시작일)
 * @param terms 규칙 버전마다의 기수당 면제권과 수동 인증 방식. 하나 이상
 */
public record MemberStudyRules(MemberStudy study, List<RuleTerms> terms) {

	/**
	 * 규칙 버전 하나의 기수당 면제권과 수동 인증 방식 (R1).
	 *
	 * <p>기수당 면제권은 다음 기수부터 바뀌는 항목이라 한 기수 안에서는 같다 (R2, OD-08). 그래서 그 기수의 어느 날의 버전으로 봐도 된다.
	 * 수동 인증 방식은 다음 스터디 일부터 바뀐다 (R2).
	 *
	 * @param effectiveFrom 버전을 적용하기 시작하는 스터디 일
	 * @param exemptionsPerSeason 기수당 면제권 (0~10)
	 * @param selfReportMode 수동 인증 방식
	 */
	public record RuleTerms(LocalDate effectiveFrom, int exemptionsPerSeason, SelfReportMode selfReportMode) {

		public RuleTerms {
			Objects.requireNonNull(effectiveFrom, "effectiveFrom");
			Objects.requireNonNull(selfReportMode, "selfReportMode");
		}

	}

	public MemberStudyRules {
		Objects.requireNonNull(study, "study");
		Objects.requireNonNull(terms, "terms");
		if (terms.isEmpty()) {
			throw new IllegalArgumentException("규칙 버전이 하나 이상 있어야 해요");
		}
		terms = terms.stream().sorted(Comparator.comparing(RuleTerms::effectiveFrom)).toList();
	}

	/** 그 스터디 일의 규칙 버전의 항목. 첫 버전보다 앞선 날은 첫 버전이다 ({@link MemberStudy#dailyGoalOn}과 같음). */
	public RuleTerms termsOn(LocalDate studyDay) {
		Objects.requireNonNull(studyDay, "studyDay");
		RuleTerms found = this.terms.getFirst();
		for (RuleTerms term : this.terms) {
			if (term.effectiveFrom().isAfter(studyDay)) {
				break;
			}
			found = term;
		}
		return found;
	}

}
