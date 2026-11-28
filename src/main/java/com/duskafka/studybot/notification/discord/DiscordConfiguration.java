package com.duskafka.studybot.notification.discord;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 디스코드 빈 (S14). 테스트를 먼저 컴파일하려는 뼈대다. */
@Configuration(proxyBeanMethods = false)
class DiscordConfiguration {

	@Bean
	DiscordWebhookCipher discordWebhookCipher() {
		return new DiscordWebhookCipher("", null);
	}

}
