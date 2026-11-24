package com.duskafka.studybot.github.push;

import com.duskafka.studybot.github.webhook.GithubEvent;
import com.duskafka.studybot.github.webhook.GithubEventHandler;
import com.duskafka.studybot.github.webhook.GithubWebhookEvent;
import com.duskafka.studybot.github.webhook.HandlingResult;
import org.springframework.stereotype.Component;

/** 뼈대: S08 구현 전. */
@Component
class PushEventHandler implements GithubEventHandler {

	@Override
	public GithubEvent event() {
		return GithubEvent.PUSH;
	}

	@Override
	public HandlingResult handle(GithubWebhookEvent event) {
		throw new UnsupportedOperationException("S08 구현 전");
	}

}
