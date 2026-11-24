package com.duskafka.studybot.verification;

import java.util.List;
import java.util.Objects;

/**
 * push 웹훅의 커밋 하나에서 풀이 기록에 필요한 값만 (5.3 'push 처리 순서' 3). 작성자 이름·이메일은 받지 않는다 (R16, 9.8).
 *
 * @param sha 커밋 SHA ({@code commits[].id})
 * @param message 커밋 메시지. 파서에만 넣고 저장하지 않는다
 * @param changedPaths 추가·수정된 파일 경로 ({@code added} 다음 {@code modified}). 지운 파일은 넣지 않는다
 */
public record GithubCommit(String sha, String message, List<String> changedPaths) {

	public GithubCommit {
		Objects.requireNonNull(sha, "sha");
		Objects.requireNonNull(message, "message");
		changedPaths = List.copyOf(changedPaths);
	}

}
