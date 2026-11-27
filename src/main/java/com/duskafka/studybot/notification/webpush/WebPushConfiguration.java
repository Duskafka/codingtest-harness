package com.duskafka.studybot.notification.webpush;

import com.duskafka.studybot.notification.persistence.PushSubscriptionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 웹 푸시 빈 (S13 만들 것 5·6). VAPID 키는 기동할 때 한 번 읽고 검사한다. 없거나 틀리면 앱이 뜨지 않는다 ({@link VapidKeys}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebPushProperties.class)
class WebPushConfiguration {

	@Bean
	VapidKeys vapidKeys(WebPushProperties properties) {
		return VapidKeys.from(properties.vapid());
	}

	@Bean
	PushEndpointPolicy pushEndpointPolicy(WebPushProperties properties) {
		return new PushEndpointPolicy(properties.allowedHosts());
	}

	@Bean
	WebPushSender webPushSender(VapidKeys vapidKeys, PushSubscriptionRepository subscriptions,
			MeterRegistry meterRegistry) {
		return new WebPushSender(WebPushSender.httpClient(), vapidKeys, subscriptions, meterRegistry);
	}

}
