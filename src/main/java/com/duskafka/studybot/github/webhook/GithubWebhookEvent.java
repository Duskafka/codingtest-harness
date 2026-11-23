package com.duskafka.studybot.github.webhook;

import java.time.Instant;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * 처리기에 넘기는 웹훅 하나. 서명을 확인했고 수신함에 남긴 뒤다.
 *
 * @param inboxId 수신함 행 ID
 * @param deliveryId {@code X-GitHub-Delivery}. GitHub가 전달마다 붙이는 ID다 (재전송이면 같은 값)
 * @param event 이벤트 종류
 * @param payload 본문 JSON. 처리기가 자기 레코드로 바꿔 쓴다 (예: {@code JsonMapper#treeToValue})
 * @param receivedAt 서버가 받은 시각 = 수신함의 {@code received_at}. push의 풀이 인정 시각이다 (D5, 5.3 'push 처리 순서' 6)
 */
public record GithubWebhookEvent(long inboxId, String deliveryId, GithubEvent event, JsonNode payload,
		Instant receivedAt) {

	public GithubWebhookEvent {
		Objects.requireNonNull(deliveryId, "deliveryId");
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(payload, "payload");
		Objects.requireNonNull(receivedAt, "receivedAt");
	}

}
