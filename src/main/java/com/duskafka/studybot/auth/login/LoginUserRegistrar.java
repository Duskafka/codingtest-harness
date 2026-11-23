package com.duskafka.studybot.auth.login;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.auth.UserLoggedIn;
import com.duskafka.studybot.auth.persistence.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GitHub로 확인한 사람을 {@code users}에 저장하거나 갱신하고, 세션에 담을 {@link LoginUser}를 만든다 (S04 만들 것 2).
 * 다시 로그인하면 GitHub 로그인 이름과 아바타만 갱신한다. 가입 시각은 주입된 {@link Clock}으로 정한다.
 *
 * <p>로그인할 때마다 {@link UserLoggedIn}을 발행한다. 받는 쪽은 커밋 뒤에 처리한다 (예: S07 가입 전 설치 연결).
 */
@Service
@RequiredArgsConstructor
class LoginUserRegistrar {

	private final UserRepository userRepository;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	@Transactional
	LoginUser register(long githubId, String githubLogin, @Nullable String avatarUrl) {
		long userId = this.userRepository.upsertByGithubId(githubId, githubLogin, avatarUrl, this.clock.instant());
		this.events.publishEvent(new UserLoggedIn(userId, githubId));
		return new LoginUser(userId, githubId);
	}

}
