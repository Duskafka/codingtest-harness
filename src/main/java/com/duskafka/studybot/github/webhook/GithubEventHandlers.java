package com.duskafka.studybot.github.webhook;

import java.util.List;
import java.util.Optional;

class GithubEventHandlers {

	GithubEventHandlers(List<GithubEventHandler> handlers) {
	}

	Optional<GithubEventHandler> find(GithubEvent event) {
		throw new UnsupportedOperationException("S06 구현 전");
	}

}
