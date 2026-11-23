package com.duskafka.studybot.github.webhook;

import org.springframework.stereotype.Component;

/**
 * {@code ping}: 웹훅을 만들 때 GitHub가 보내는 확인 요청. 할 일이 없어 바로 PROCESSED다.
 * 수신함에는 남긴다. App 웹훅 설정이 맞게 됐다는 기록이 된다 (사람이 정함, 2026-11-23 S06 세션 "수신함에 넣고 200").
 */
@Component
class PingEventHandler implements GithubEventHandler {

	@Override
	public GithubEvent event() {
		return GithubEvent.PING;
	}

	@Override
	public HandlingResult handle(GithubWebhookEvent event) {
		return HandlingResult.PROCESSED;
	}

}
