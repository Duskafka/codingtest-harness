package com.duskafka.studybot.auth;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 다른 모듈이 화면에 보여 줄 사용자 정보 ({@link UserDirectory#profilesOf}). GitHub 로그인 이름과 아바타만 담는다 (9.8 '최소 수집').
 *
 * @param userId {@code users.id}
 * @param login GitHub 로그인 이름. 탈퇴(익명화)한 사용자는 익명화한 값이다 (5.16, S15)
 * @param avatarUrl 아바타 주소. 없으면 {@code null}
 */
public record UserProfile(long userId, String login, @Nullable String avatarUrl) {

	public UserProfile {
		Objects.requireNonNull(login, "login");
	}

}
