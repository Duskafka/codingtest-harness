package com.duskafka.studybot.github.webhook;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GithubWebhookProperties.class)
class GithubWebhookConfiguration {

	@Bean
	GithubSignatureVerifier githubSignatureVerifier(GithubWebhookProperties properties) {
		return new GithubSignatureVerifier(properties.secret(), properties.previousSecret());
	}

}
