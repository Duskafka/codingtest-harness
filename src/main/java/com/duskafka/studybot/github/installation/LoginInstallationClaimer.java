package com.duskafka.studybot.github.installation;

import com.duskafka.studybot.auth.UserLoggedIn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그인(가입 포함)이 커밋된 뒤, 가입하기 전에 남겨 둔 설치를 그 사용자에게 잇는다 (5.2 처리 순서 2, S07 만들 것 2).
 *
 * <p>커밋 뒤에는 원래 트랜잭션에 참여해도 커밋되지 않으므로 새 트랜잭션을 연다. 실패해도 로그인은 그대로 성공한다.
 * 실패하거나 이벤트가 사라져도 연결 화면을 볼 때 다시 잇는다 ({@code GithubConnectionService}). 웹훅과 가입이 동시에 커밋돼
 * 서로 못 보는 경우도 그때 맞춰진다.
 */
@Slf4j
@Component
class LoginInstallationClaimer {

	private final GithubInstallationLinker linker;

	private final TransactionTemplate newTransaction;

	LoginInstallationClaimer(GithubInstallationLinker linker, PlatformTransactionManager transactionManager) {
		this.linker = linker;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onLogin(UserLoggedIn event) {
		try {
			Integer claimed = this.newTransaction.execute(status -> this.linker.claim(event.userId(), event.githubId()));
			if (claimed != null && claimed > 0) {
				log.info("가입 전에 남긴 GitHub App 설치를 이었어요: user={}, installations={}", event.userId(), claimed);
			}
		}
		catch (RuntimeException ex) {
			log.warn("로그인 뒤 GitHub App 설치를 잇지 못했어요. 연결 화면을 볼 때 다시 이어요: user={}", event.userId(), ex);
		}
	}

}
