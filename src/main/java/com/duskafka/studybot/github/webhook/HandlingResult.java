package com.duskafka.studybot.github.webhook;

/** 처리기가 정상으로 끝났을 때의 결과. 수신함 상태가 된다. 실패는 예외로 알린다 ({@link GithubEventHandler}). */
public enum HandlingResult {

	/** 처리했다 → {@code PROCESSED}. */
	PROCESSED,

	/** 처리할 것이 없었다 (예: 기본 브랜치가 아닌 push, 연결하지 않은 레포) → {@code IGNORED}. */
	IGNORED

}
