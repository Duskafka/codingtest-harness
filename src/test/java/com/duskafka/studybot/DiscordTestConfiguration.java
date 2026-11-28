package com.duskafka.studybot;

import com.duskafka.studybot.notification.DiscordStub;
import com.duskafka.studybot.notification.DiscordTestKeys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * 디스코드 설정을 테스트 값으로 채운다 (S14). 모든 통합 테스트 컨텍스트가 같은 값을 받아 컨텍스트 공유 구조는 그대로다
 * ({@link GithubAppTestConfiguration}과 같은 방식).
 *
 * <ul>
 * <li>API 주소: {@link DiscordStub}(WireMock). 테스트는 실제 디스코드에 접속하지 않는다.</li>
 * <li>웹훅 URL 암호화 키: 테스트 안에서 만든 지금 키와 옛 키({@link DiscordTestKeys}). 교체 중 상태(옛 키로도 풂)를 기본으로 둔다.
 * 운영에서는 환경 변수로만 받는다 (main application.yaml, infra.md 9.6).</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
class DiscordTestConfiguration {

	@Bean
	DynamicPropertyRegistrar discordProperties() {
		return registry -> {
			registry.add("studybot.discord.api-base-url", DiscordStub::apiBaseUrl);
			registry.add("studybot.discord.encryption.key", DiscordTestKeys::current);
			registry.add("studybot.discord.encryption.previous-key", DiscordTestKeys::previous);
		};
	}

}
