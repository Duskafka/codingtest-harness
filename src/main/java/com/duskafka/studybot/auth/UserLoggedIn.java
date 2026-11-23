package com.duskafka.studybot.auth;

/**
 * 사용자가 GitHub로 로그인했다 (처음 가입한 때 포함). 로그인 트랜잭션 안에서 발행하고, 받는 쪽은 커밋 뒤에 처리한다.
 *
 * <p>{@code auth}는 다른 모듈에 의존하지 않으므로(결정 001 ③), 로그인을 다른 모듈에 알릴 때 이 이벤트를 쓴다
 * (architecture.md 4.2 '반대 방향 통지는 트랜잭션 커밋 뒤에 발행하는 이벤트로'). 예: 가입하기 전에 설치한 GitHub App을
 * 이 사용자에게 잇는다 (5.2 처리 순서 2, S07). 이벤트는 사라질 수 있으니 받는 쪽은 DB 상태로 다시 맞출 길을 따로 둔다.
 *
 * @param userId {@code users.id}
 * @param githubId GitHub 사용자 ID
 */
public record UserLoggedIn(long userId, long githubId) {
}
