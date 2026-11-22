package com.duskafka.studybot.verification;

import java.util.List;

/**
 * 커밋 하나를 {@link ProblemParser}로 분석한 결과.
 *
 * <p>5.4 흐름도의 '확인 대기'는 연결할 때 지정한 경로 접두사가 있어야 판단할 수 있다. 파서는 접두사를 모르고,
 * 식별하지 못한 풀이 파일 후보만 {@link #unidentifiedPaths()}로 돌려준다. 접두사와 비교해 확인 대기로 남길지는
 * 호출하는 쪽(S08)이 정한다. README 같은 풀이 파일이 아닌 파일은 어느 목록에도 들어가지 않는다 ('무시').
 *
 * @param problems 식별한 문제. 문제당 하나, 경로 목록에서 처음 나온 순서
 * @param unidentifiedPaths 식별하지 못한 풀이 파일 후보의 원본 경로. 입력 순서, 중복 없음
 */
public record ParsedCommit(List<IdentifiedProblem> problems, List<String> unidentifiedPaths) {

	private static final ParsedCommit EMPTY = new ParsedCommit(List.of(), List.of());

	public ParsedCommit {
		problems = List.copyOf(problems);
		unidentifiedPaths = List.copyOf(unidentifiedPaths);
	}

	/** 식별한 문제도, 확인 대기 후보도 없는 결과. */
	public static ParsedCommit empty() {
		return EMPTY;
	}

	/** 식별한 문제의 식별자만. */
	public List<ProblemRef> refs() {
		return problems.stream().map(IdentifiedProblem::ref).toList();
	}

}
