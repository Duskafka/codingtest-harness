package com.duskafka.studybot.github.webhook;

import java.util.Arrays;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 받는 GitHub 웹훅 이벤트 (S06 만들 것 3, 5.3 의사코드). 이 밖의 이벤트는 서명을 확인한 뒤 204로 답하고 남기지 않는다.
 * 여기 있는 이벤트는 모두 처리기가 있어야 앱이 뜬다 (S08 만들 것 7).
 * 값은 {@code X-GitHub-Event} 헤더 값이다.
 */
public enum GithubEvent {

	/** 커밋 push. 처리기는 {@code github.push.PushEventHandler} (S08). */
	PUSH("push"),

	/** App 설치·삭제·정지. 처리기는 {@code github.installation.InstallationEventHandler} (S07). */
	INSTALLATION("installation"),

	/** 설치에 레포를 더하거나 뺌. 처리기는 {@code github.installation.InstallationRepositoriesEventHandler} (S07). */
	INSTALLATION_REPOSITORIES("installation_repositories"),

	/** 웹훅을 만들 때 GitHub가 보내는 확인 요청. 처리기는 {@link PingEventHandler}. */
	PING("ping");

	private final String headerValue;

	GithubEvent(String headerValue) {
		this.headerValue = headerValue;
	}

	/** {@code X-GitHub-Event} 헤더 값 (예: {@code push}). 수신함의 {@code event_type}에도 이 값을 남긴다. */
	public String headerValue() {
		return headerValue;
	}

	/** 헤더 값에 맞는 이벤트. 받지 않는 이벤트면 빈 값이다. 대소문자를 구분한다 (GitHub는 소문자로 보낸다). */
	public static Optional<GithubEvent> fromHeader(@Nullable String value) {
		return Arrays.stream(values()).filter(event -> event.headerValue.equals(value)).findFirst();
	}

}
