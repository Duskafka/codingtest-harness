package com.duskafka.studybot.github.api;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;

/**
 * 설치 토큰 캐시 (S07 만들 것 4, '판단 기록이 필요한 곳': 캐시 위치와 갱신 시점).
 *
 * <ul>
 * <li><b>위치:</b> 앱 메모리. 서버가 한 대이고(D10) 토큰은 1시간짜리라 다시 뜨면 새로 받으면 된다. DB에 두지 않는다 (토큰을 저장하지 않게).</li>
 * <li><b>갱신 시점:</b> 만료 5분 전부터 새로 받는다. 받은 토큰으로 레포 목록을 여러 페이지 읽는 동안 만료되지 않게 여유를 둔다.
 * 그 전에는 다시 발급하지 않는다 (설치 토큰 요청 한도 [S38]).</li>
 * <li><b>동시성:</b> 설치마다 잠금을 따로 둬서 같은 설치에 동시에 와도 한 번만 발급한다. 다른 설치는 서로 기다리지 않는다.</li>
 * </ul>
 *
 * 시간은 주입된 {@link Clock}으로만 본다.
 */
public class InstallationTokenCache {

	static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);

	private final GithubApiClient client;

	private final Clock clock;

	private final ConcurrentMap<Long, InstallationToken> tokens = new ConcurrentHashMap<>();

	private final ConcurrentMap<Long, Object> locks = new ConcurrentHashMap<>();

	public InstallationTokenCache(GithubApiClient client, Clock clock) {
		this.client = client;
		this.clock = clock;
	}

	/** 이 설치의 유효한 토큰. 없거나 만료가 가까우면 새로 받는다. */
	public String tokenFor(long installationId) {
		InstallationToken cached = this.tokens.get(installationId);
		if (isFresh(cached)) {
			return cached.value();
		}
		synchronized (this.locks.computeIfAbsent(installationId, id -> new Object())) {
			cached = this.tokens.get(installationId);
			if (isFresh(cached)) {
				return cached.value();
			}
			InstallationToken issued = this.client.createInstallationToken(installationId);
			this.tokens.put(installationId, issued);
			return issued.value();
		}
	}

	/** 이 설치의 토큰을 버린다 (예: GitHub가 토큰을 거절했을 때). 다음 호출에서 새로 받는다. */
	public void evict(long installationId) {
		this.tokens.remove(installationId);
	}

	private boolean isFresh(@Nullable InstallationToken token) {
		return token != null && this.clock.instant().isBefore(token.expiresAt().minus(REFRESH_BEFORE_EXPIRY));
	}

}
