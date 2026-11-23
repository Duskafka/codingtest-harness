package com.duskafka.studybot.github.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 처리기 연결 (S06 만들 것 5): 이벤트 종류로 처리기를 찾는다. */
class GithubEventHandlersTest {

	@Test
	@DisplayName("S06: 이벤트 종류로 처리기를 찾고, 없으면 빈 값이다")
	void findsHandlerByEvent() {
		GithubEventHandler push = handler(GithubEvent.PUSH);
		GithubEventHandlers handlers = new GithubEventHandlers(List.of(push, handler(GithubEvent.PING)));

		assertThat(handlers.find(GithubEvent.PUSH)).containsSame(push);
		assertThat(handlers.find(GithubEvent.INSTALLATION)).isEmpty();
	}

	@Test
	@DisplayName("S06: 같은 이벤트에 처리기가 둘이면 만들 수 없다 (앱이 뜨지 않음)")
	void rejectsTwoHandlersForSameEvent() {
		assertThatThrownBy(
				() -> new GithubEventHandlers(List.of(handler(GithubEvent.PUSH), handler(GithubEvent.PUSH))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("push");
	}

	private static GithubEventHandler handler(GithubEvent event) {
		return new GithubEventHandler() {

			@Override
			public GithubEvent event() {
				return event;
			}

			@Override
			public HandlingResult handle(GithubWebhookEvent webhookEvent) {
				return HandlingResult.PROCESSED;
			}

		};
	}

}
