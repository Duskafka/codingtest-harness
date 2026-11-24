package com.duskafka.studybot.verification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 풀이로 볼 push 하나: 연결 레포의 기본 브랜치에 올라온 커밋들 ({@link GithubSolveRecorder}).
 * 기본 브랜치인지, 연결한 레포인지는 github 모듈이 이미 확인했다.
 *
 * @param userId 연결 레포의 주인. 커밋 작성자가 아니라 연결한 사용자의 풀이로 본다
 * @param repoId GitHub 레포 ID
 * @param inboxId 이 push를 남긴 수신함 행
 * @param receivedAt 서버가 웹훅을 받은 시각 = 수신함의 {@code received_at}. 풀이 인정 시각이다 (R3, D5)
 * @param pathPrefix 연결할 때 지정한 경로 접두사. 비어 있으면 레포 전체다 (사람이 정함, 2026-11-24 S08 세션 "레포 전체로 봄")
 * @param commits 커밋 목록. payload 순서 그대로
 */
public record GithubPush(long userId, long repoId, long inboxId, Instant receivedAt, @Nullable String pathPrefix,
		List<GithubCommit> commits) {

	public GithubPush {
		Objects.requireNonNull(receivedAt, "receivedAt");
		commits = List.copyOf(commits);
	}

}
