package com.duskafka.studybot.github.persistence;

/**
 * 수신함 행의 상태 (glossary.md '웹훅 수신함', 5.3 상태 그림).
 *
 * <ul>
 * <li>{@link #RECEIVED}: 받아서 남겼고 아직 처리를 마치지 않았다.</li>
 * <li>{@link #PROCESSED}: 처리했다.</li>
 * <li>{@link #IGNORED}: 처리할 것이 없었다 (관심 없는 브랜치, 연결하지 않은 레포, 아직 처리기가 없는 이벤트).</li>
 * <li>{@link #FAILED}: 처리하다 실패했다. 오류는 {@code last_error}에 있다.</li>
 * <li>{@link #DEAD}: 재시도를 다 썼다 (P2 워커).</li>
 * </ul>
 */
public enum WebhookInboxStatus {

	RECEIVED, PROCESSED, IGNORED, FAILED, DEAD

}
