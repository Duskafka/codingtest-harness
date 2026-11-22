package com.duskafka.studybot.verification;

import java.util.List;

/**
 * 커밋 메시지와 파일 경로로 문제를 식별한다 (docs/spec/processes/5.04-problem-identification.md). DB를 모르는 순수 함수다.
 */
public final class ProblemParser {

	/** 파서 버전. 식별 규칙을 바꾸면 올린다. 풀이 기록에 남긴다 (5.4, S08). */
	public static final int VERSION = 1;

	private ProblemParser() {
	}

	public static ParsedCommit parse(String commitMessage, List<String> paths) {
		throw new UnsupportedOperationException("S03 구현 전");
	}

}
