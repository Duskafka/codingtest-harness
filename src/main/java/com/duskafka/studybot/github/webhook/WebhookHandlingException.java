package com.duskafka.studybot.github.webhook;

/**
 * 처리기가 실패를 알리면서 수신함 {@code last_error}에 남길 설명을 함께 주는 예외.
 *
 * <p>{@code last_error}에는 예외 종류만 남고 메시지는 남지 않는다 (사람이 정함, 2026-11-23 S06 세션
 * "메시지 대신 예외 종류만"). 이 예외의 메시지만 예외로 그대로 남는다. 그래서 메시지에는 개인정보(이메일, 이름, GitHub 로그인 등)와
 * payload에서 꺼낸 값을 넣지 않는다. 예: "연결하지 않은 레포예요".
 */
public class WebhookHandlingException extends RuntimeException {

	public WebhookHandlingException(String safeMessage) {
		super(safeMessage);
	}

	public WebhookHandlingException(String safeMessage, Throwable cause) {
		super(safeMessage, cause);
	}

}
