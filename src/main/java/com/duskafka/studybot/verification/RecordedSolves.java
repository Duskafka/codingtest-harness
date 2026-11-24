package com.duskafka.studybot.verification;

/**
 * push 하나로 새로 남긴 풀이 기록 수 ({@link GithubSolveRecorder}). 이미 있던 기록(같은 push를 다시 받음)은 세지 않는다.
 *
 * @param confirmed 문제를 식별한 기록 ({@code CONFIRMED})
 * @param pending 확인 대기 기록 ({@code PENDING_IDENTIFICATION})
 */
public record RecordedSolves(int confirmed, int pending) {

}
