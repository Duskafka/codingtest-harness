package com.duskafka.studybot.github.installation;

import com.duskafka.studybot.auth.UserLoggedIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 로그인(가입 포함)이 커밋된 뒤, 가입하기 전에 남겨 둔 설치를 그 사용자에게 잇는다 (5.2 처리 순서 2, S07 만들 것 2).
 *
 * <p>다른 스레드에서 한다({@code @Async}). 커밋 콜백은 로그인 요청 스레드에서 원래 DB 연결을 쥔 채 불리므로, 그 자리에서 새
 * 트랜잭션을 열면 두 번째 연결을 기다리며 로그인이 늦어질 수 있다 (PR #14 리뷰 반영, 사람이 정함 2026-11-23 "비동기로 실행").
 * {@link GithubInstallationLinker#claim}이 자기 트랜잭션을 연다.
 *
 * <p>실패해도 로그인은 그대로 성공한다. 실패하거나 작업이 사라져도 연결 화면을 볼 때 다시 잇는다 ({@code GithubConnectionService}).
 * 웹훅과 가입이 동시에 커밋돼 서로 못 보는 경우도 그때 맞춰진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LoginInstallationClaimer {

	private final GithubInstallationLinker linker;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onLogin(UserLoggedIn event) {
		try {
			int claimed = this.linker.claim(event.userId(), event.githubId());
			if (claimed > 0) {
				log.info("가입 전에 남긴 GitHub App 설치를 이었어요: user={}, installations={}", event.userId(), claimed);
			}
		}
		catch (RuntimeException ex) {
			log.warn("로그인 뒤 GitHub App 설치를 잇지 못했어요. 연결 화면을 볼 때 다시 이어요: user={}", event.userId(), ex);
		}
	}

}
