package com.duskafka.studybot.github.webhook;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 이벤트 종류 → 처리기 (S06 만들 것 5). 처리기는 스프링 빈으로 모은다. 같은 이벤트에 처리기가 둘이면 앱이 뜨지 않는다. */
@Component
class GithubEventHandlers {

	private final Map<GithubEvent, GithubEventHandler> byEvent = new EnumMap<>(GithubEvent.class);

	GithubEventHandlers(List<GithubEventHandler> handlers) {
		for (GithubEventHandler handler : handlers) {
			GithubEventHandler existing = byEvent.putIfAbsent(handler.event(), handler);
			if (existing != null) {
				throw new IllegalStateException("이벤트 %s에 처리기가 둘이에요: %s, %s".formatted(handler.event().headerValue(),
						existing.getClass().getName(), handler.getClass().getName()));
			}
		}
	}

	/** 뼈대: S08 구현 전. */
	void requireAllEvents() {
	}

	Optional<GithubEventHandler> find(GithubEvent event) {
		return Optional.ofNullable(byEvent.get(event));
	}

}
