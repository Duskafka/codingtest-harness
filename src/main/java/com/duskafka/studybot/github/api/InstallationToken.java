package com.duskafka.studybot.github.api;

import java.time.Instant;
import java.util.Objects;

/**
 * 설치 토큰. 1시간 동안 그 설치의 레포를 읽을 수 있다. 값은 메모리에만 두고 DB·로그·응답에 남기지 않는다.
 *
 * @param value 토큰 값. 길이·모양을 가정하지 않는다 (GitHub가 2026-04부터 형식을 바꾸는 중)
 * @param expiresAt 만료 시각
 */
public record InstallationToken(String value, Instant expiresAt) {

	public InstallationToken {
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(expiresAt, "expiresAt");
	}

	/** 토큰 값이 로그에 찍히지 않게 가린다. */
	@Override
	public String toString() {
		return "InstallationToken[value=***, expiresAt=" + this.expiresAt + "]";
	}

}
