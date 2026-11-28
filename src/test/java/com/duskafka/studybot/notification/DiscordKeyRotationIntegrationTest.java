package com.duskafka.studybot.notification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.notification.DiscordStub.Webhook;
import com.duskafka.studybot.notification.discord.DiscordKeyRotation;
import com.duskafka.studybot.notification.discord.DiscordWebhookCipher;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 디스코드 웹훅 URL 암호화 키 교체 (infra.md 9.6 '키 교체 후 저장된 URL 재암호화', S14 판단 기록 '암호화 방식과 키 교체 절차').
 *
 * <p>테스트 컨텍스트는 교체 중 상태다: 지금 키와 옛 키를 함께 받는다 ({@code DiscordTestConfiguration}).
 */
@IntegrationTest
class DiscordKeyRotationIntegrationTest {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private DiscordKeyRotation rotation;

	@Autowired
	private DiscordWebhookCipher cipher;

	private StudyDayRows rows;

	private NotificationRows notifications;

	private TestApi api;

	private long studyId;

	private long owner;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		notifications = new NotificationRows(jdbc);
		api = new TestApi(mvc, jdbc);
		TestStudy study = rows.standardStudy(rows.newUser("s14-rotate"));
		studyId = study.studyId();
		owner = notifications.ownerOf(studyId);
		rows.joinAsOwner(studyId, owner, NOV_1);
		clock.setInstant(seoul(NOV_20, 21, 0));
	}

	@Test
	@DisplayName("옛 키로 저장된 웹훅 주소를 지금 키로 다시 암호화한다. 다시 돌려도 그대로이고, 다시 암호화한 주소로 게시된다")
	void rewrapsRowsEncryptedWithPreviousKey() {
		Webhook webhook = DiscordStub.newWebhook();
		DiscordStub.reply(webhook, 204);
		assertThat(api.putJson(owner, "/api/studies/{id}/channels/discord",
				"{\"webhookUrl\":\"" + webhook.officialUrl() + "\"}", studyId))
			.hasStatusOk();
		String byOldKey = new DiscordWebhookCipher(DiscordTestKeys.previous(), null).encrypt(studyId,
				webhook.officialUrl());
		notifications.setChannelCiphertext(studyId, byOldKey);

		int rewrapped = rotation.rewrap();

		assertThat(rewrapped).isGreaterThanOrEqualTo(1);
		String after = notifications.channel(studyId).orElseThrow().ciphertext();
		assertThat(after).isNotEqualTo(byOldKey).startsWith("v1:" + cipher.currentKeyId() + ":");
		assertThat(cipher.isCurrent(after)).isTrue();

		rotation.rewrap();
		assertThat(notifications.channel(studyId).orElseThrow().ciphertext()).isEqualTo(after);

		assertThat(api.postJson(owner, "/api/studies/{id}/channels/discord/test", "", studyId)).hasStatus(HttpStatus.OK);
		assertThat(DiscordStub.requestsTo(webhook)).hasSize(1);
	}

}
