package com.duskafka.studybot.auth;

import com.duskafka.studybot.auth.persistence.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 모듈이 사용자를 찾을 때 쓰는 공개 API. 엔티티는 내주지 않고 필요한 값만 준다. */
@Service
@RequiredArgsConstructor
public class UserDirectory {

	private final UserRepository userRepository;

	/** 이 GitHub ID로 가입한(탈퇴하지 않은) 사용자의 ID. 탈퇴하면 GitHub ID를 지우므로 찾지 못한다 (5.16). */
	@Transactional(readOnly = true)
	public Optional<Long> findUserIdByGithubId(long githubId) {
		return this.userRepository.findActiveIdByGithubId(githubId);
	}

}
