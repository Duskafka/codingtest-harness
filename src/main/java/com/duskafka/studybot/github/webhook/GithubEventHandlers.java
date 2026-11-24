package com.duskafka.studybot.github.webhook;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 이벤트 종류 → 처리기 (S06 만들 것 5). 처리기는 스프링 빈으로 모은다.
 *
 * <p>앱이 뜨려면 두 가지를 지켜야 한다.
 * <ul>
 * <li>같은 이벤트에 처리기가 둘이면 안 된다 (생성자에서 검사).</li>
 * <li>받는 이벤트({@link GithubEvent}) 모두 처리기가 있어야 한다 ({@link #requireAllEvents()}, S08 만들 것 7). S06의
 * "처리기가 없으면 IGNORED" 임시 분기를 없앤 대신이다 (사람이 정함, 2026-11-23 S06 세션 "S08에서 처리기 필수로").
 * 생성자가 아니라 빈 초기화 때 검사해서, 이 클래스를 직접 만드는 단위 테스트는 처리기 일부만으로도 찾기를 확인할 수 있다.</li>
 * </ul>
 */
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

	/** 받는 이벤트 모두 처리기가 있는지 본다. 없으면 빈을 만들 수 없어 앱이 뜨지 않는다. */
	@PostConstruct
	void requireAllEvents() {
		String missing = Arrays.stream(GithubEvent.values())
			.filter(event -> !byEvent.containsKey(event))
			.map(GithubEvent::headerValue)
			.collect(Collectors.joining(", "));
		if (!missing.isEmpty()) {
			throw new IllegalStateException("처리기가 없는 웹훅 이벤트가 있어요: " + missing);
		}
	}

	Optional<GithubEventHandler> find(GithubEvent event) {
		return Optional.ofNullable(byEvent.get(event));
	}

}
