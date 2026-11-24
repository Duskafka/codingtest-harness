package com.duskafka.studybot.github.push;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code push} 웹훅에서 쓰는 값만 담은 모양
 * (<a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#push">GitHub 문서</a>).
 * 커밋 작성자·커미터·pusher·sender(이름과 이메일)는 매핑하지 않는다. 읽지도 넘기지도 않는다 (R16, security.md 9.8).
 *
 * @param ref 올라온 ref (예: {@code refs/heads/main}, {@code refs/tags/v1})
 * @param deleted ref를 지운 push인지
 * @param before push 전 SHA
 * @param after push 뒤 SHA
 * @param repository 레포
 * @param commits 커밋 목록. GitHub가 자를 수 있다 (5.3 '주의할 것')
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PushPayload(@Nullable String ref, @Nullable Boolean deleted, @Nullable String before, @Nullable String after,
		@Nullable Repository repository, @Nullable List<Commit> commits) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Repository(@Nullable Long id) {
	}

	/**
	 * @param id 커밋 SHA
	 * @param message 커밋 메시지
	 * @param added 더한 파일 경로
	 * @param modified 고친 파일 경로
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Commit(@Nullable String id, @Nullable String message, @Nullable List<String> added,
			@Nullable List<String> modified) {
	}

}
