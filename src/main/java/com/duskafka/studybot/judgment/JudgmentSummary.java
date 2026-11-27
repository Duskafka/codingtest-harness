package com.duskafka.studybot.judgment;

/**
 * 판정 배치 한 번(또는 스터디 하나)의 결과 (S12 만들 것 8 '지표'). 로그와 테스트가 본다.
 *
 * @param studies 돈 스터디 수
 * @param judgedDays 진행 지점을 올린 날 수 (따라잡은 날 포함)
 * @param judgedMembers 이번에 결과를 남긴 멤버 일 수 (이미 결과가 있어 건너뛴 것은 빼고)
 * @param errors 오류 수 (멤버 판정, 기수 준비, 판정할 날 조회)
 */
public record JudgmentSummary(int studies, int judgedDays, int judgedMembers, int errors) {

	static final JudgmentSummary EMPTY = new JudgmentSummary(0, 0, 0, 0);

	JudgmentSummary plus(JudgmentSummary other) {
		return new JudgmentSummary(this.studies + other.studies, this.judgedDays + other.judgedDays,
				this.judgedMembers + other.judgedMembers, this.errors + other.errors);
	}

}
