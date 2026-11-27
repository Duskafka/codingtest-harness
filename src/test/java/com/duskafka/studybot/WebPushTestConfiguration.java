package com.duskafka.studybot;

import com.duskafka.studybot.notification.WebPushTestKeys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * 웹 푸시 VAPID 키를 테스트 안에서 만든 키로 채운다 (S13 '주의할 것'). 모든 통합 테스트 컨텍스트가 같은 값을 받아 컨텍스트 공유 구조는
 * 그대로다 ({@link GithubAppTestConfiguration}과 같은 방식). 운영에서는 환경 변수로만 받는다 (main application.yaml, infra.md 9.6).
 * subject 같은 고정 가짜 값은 {@code src/test/resources/config/application.yaml}에 있다.
 */
@TestConfiguration(proxyBeanMethods = false)
class WebPushTestConfiguration {

	@Bean
	DynamicPropertyRegistrar webPushProperties() {
		return registry -> {
			registry.add("studybot.webpush.vapid.public-key", WebPushTestKeys::vapidPublicKeyBase64Url);
			registry.add("studybot.webpush.vapid.private-key", WebPushTestKeys::vapidPrivateKeyBase64Url);
		};
	}

}
