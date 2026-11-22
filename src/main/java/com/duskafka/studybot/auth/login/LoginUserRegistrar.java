package com.duskafka.studybot.auth.login;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.auth.persistence.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GitHub로 확인한 사람을 {@code users}에 저장하거나 갱신하고, 세션에 담을 {@link LoginUser}를 만든다 (S04 만들 것 2).
 * 다시 로그인하면 GitHub 로그인 이름과 아바타만 갱신한다. 가입 시각은 주입된 {@link Clock}으로 정한다.
 */
@Service
@RequiredArgsConstructor
class LoginUserRegistrar {

	private final UserRepository userRepository;

	private final Clock clock;

	@Transactional
	LoginUser register(long githubId, String githubLogin, @Nullable String avatarUrl) {
		long userId = this.userRepository.upsertByGithubId(githubId, githubLogin, avatarUrl, this.clock.instant());
		return new LoginUser(userId, githubId);
	}

}
