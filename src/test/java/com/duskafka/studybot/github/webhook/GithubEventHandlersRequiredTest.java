package com.duskafka.studybot.github.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * S08 만들 것 7: 받는 네 이벤트({@link GithubEvent}) 모두 처리기가 있어야 앱이 뜬다. S06의 "처리기가 없으면 IGNORED" 임시 분기를
 * 없앤 대신이다 (사람이 정함, 2026-11-23 S06 세션 "S08에서 처리기 필수로").
 */
class GithubEventHandlersRequiredTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withBean(GithubEventHandlers.class);

	@Test
	@DisplayName("S08: 받는 이벤트 중 처리기가 없는 것이 있으면 앱이 뜨지 않는다")
	void contextFailsWhenAnEventHasNoHandler() {
		runner.withBean("push", GithubEventHandler.class, () -> handler(GithubEvent.PUSH))
			.withBean("installation", GithubEventHandler.class, () -> handler(GithubEvent.INSTALLATION))
			.withBean("ping", GithubEventHandler.class, () -> handler(GithubEvent.PING))
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure()).rootCause()
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("installation_repositories");
			});
	}

	@Test
	@DisplayName("S08: 네 이벤트 모두 처리기가 있으면 앱이 뜬다")
	void contextStartsWhenEveryEventHasHandler() {
		runner.withBean("push", GithubEventHandler.class, () -> handler(GithubEvent.PUSH))
			.withBean("installation", GithubEventHandler.class, () -> handler(GithubEvent.INSTALLATION))
			.withBean("repositories", GithubEventHandler.class, () -> handler(GithubEvent.INSTALLATION_REPOSITORIES))
			.withBean("ping", GithubEventHandler.class, () -> handler(GithubEvent.PING))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(GithubEventHandlers.class).find(GithubEvent.INSTALLATION_REPOSITORIES))
					.isPresent();
			});
	}

	@Test
	@DisplayName("S08: 시작 검사는 빠진 이벤트를 모두 알려 준다")
	void requireAllEventsNamesEveryMissingEvent() {
		GithubEventHandlers handlers = new GithubEventHandlers(List.of(handler(GithubEvent.PING)));

		assertThatThrownBy(handlers::requireAllEvents).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("push")
			.hasMessageContaining("installation,")
			.hasMessageContaining("installation_repositories")
			.message()
			.doesNotContain("ping");
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
