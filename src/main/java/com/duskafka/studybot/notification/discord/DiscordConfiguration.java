package com.duskafka.studybot.notification.discord;

import com.duskafka.studybot.notification.link.PublicLinks;
import com.duskafka.studybot.notification.persistence.StudyChannelRepository;
import com.duskafka.studybot.progress.StudyDayBoards;
import com.duskafka.studybot.study.StudyProfiles;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * 디스코드 빈 (S14). 암호화 키는 기동할 때 한 번 읽고 검사한다. 없거나 틀리면 앱이 뜨지 않는다 ({@link DiscordWebhookCipher}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiscordProperties.class)
class DiscordConfiguration {

	@Bean
	DiscordWebhookCipher discordWebhookCipher(DiscordProperties properties) {
		DiscordProperties.Encryption encryption = properties.encryption();
		if (encryption == null) {
			throw new IllegalStateException("디스코드 웹훅 URL 암호화 키(studybot.discord.encryption.key)가 없어요");
		}
		return new DiscordWebhookCipher(encryption.key(), encryption.previousKey());
	}

	@Bean
	DiscordClient discordClient(DiscordProperties properties, JsonMapper jsonMapper) {
		return new DiscordClient(DiscordClient.httpClient(), properties.apiBaseUrl(), jsonMapper);
	}

	@Bean
	DiscordSender discordSender(StudyChannelRepository channels, DiscordChannels discordChannels, DiscordClient client,
			StudyProfiles studyProfiles, StudyDayBoards boards, PublicLinks links) {
		return new DiscordSender(channels, discordChannels, client, studyProfiles, boards, links);
	}

}
