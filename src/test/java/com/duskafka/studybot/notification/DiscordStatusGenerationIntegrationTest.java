package com.duskafka.studybot.notification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.notification.DiscordStub.Webhook;
import com.duskafka.studybot.notification.NotificationRows.NotificationRow;
import com.duskafka.studybot.notification.reminder.ReminderGenerator;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 알림 시각의 디스코드 현황 게시 생성 (S14 만들 것 3, 5.8 표 {@code DISCORD_STATUS}, pseudocode '마감 전 알림'의
 * {@code if study.discordEnabled}).
 *
 * <p>기본 스터디의 알림 시각은 11/20 22:00이다 (마감 24:00 − 오프셋 120분).
 */
@IntegrationTest
class DiscordStatusGenerationIntegrationTest {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private ReminderGenerator generator;

	private StudyDayRows rows;

	private NotificationRows notifications;

	private TestApi api;

	private long studyId;

	private long owner;

	private long alice;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		notifications = new NotificationRows(jdbc);
		api = new TestApi(mvc, jdbc);
		alice = rows.newUser("s14-gen-alice");
		TestStudy study = rows.standardStudy(alice);
		studyId = study.studyId();
		owner = notifications.ownerOf(studyId);
		rows.joinAsOwner(studyId, owner, NOV_1);
		rows.putMemberDay(studyId, owner, NOV_20, 1, null, null);
	}

	private void register() {
		Webhook webhook = DiscordStub.newWebhook();
		assertThat(api.putJson(owner, "/api/studies/{id}/channels/discord",
				"{\"webhookUrl\":\"" + webhook.officialUrl() + "\"}", studyId))
			.hasStatusOk();
	}

	@Test
	@DisplayName("디스코드가 켜진 스터디에 못 푼 멤버가 있으면 알림 시각에 DISCORD_STATUS를 하나 만든다 (받는 사람 없음, 만료 = 마감, 개인정보 없음)")
	void createsStatusPostOnce() {
		register();
		clock.setInstant(seoul(NOV_20, 22, 0));

		generator.remindStudy(studyId);
		generator.remindStudy(studyId);
		notifications.setLastRemindedDay(studyId, null);
		generator.remindStudy(studyId);

		assertThat(notifications.ofStudy(studyId, "DISCORD_STATUS")).singleElement().satisfies(row -> {
			assertThat(row.channel()).isEqualTo("DISCORD");
			assertThat(row.userId()).isNull();
			assertThat(row.studyDay()).isEqualTo(NOV_20);
			assertThat(row.dedupeKey()).isEqualTo("DISCORD_STATUS:" + studyId + ":" + NOV_20);
			assertThat(row.expiresAt()).isEqualTo(seoul(NOV_21, 0, 0));
			assertThat(row.status()).isEqualTo("PENDING");
			assertThat(row.payload()).doesNotContain(notifications.loginOf(alice));
		});
		assertThat(notifications.ofStudy(studyId, "REMINDER")).hasSize(1);
	}

	@Test
	@DisplayName("디스코드를 등록하지 않았거나 해제한 스터디는 DISCORD_STATUS를 만들지 않는다")
	void noStatusPostWithoutDiscord() {
		clock.setInstant(seoul(NOV_20, 22, 0));

		generator.remindStudy(studyId);

		assertThat(notifications.ofStudy(studyId, "DISCORD_STATUS")).isEmpty();
		assertThat(notifications.ofStudy(studyId, "REMINDER")).hasSize(1);

		register();
		assertThat(api.putJson(owner, "/api/studies/{id}/channels/discord", "{\"webhookUrl\":null}", studyId))
			.hasStatusOk();
		notifications.setLastRemindedDay(studyId, null);
		generator.remindStudy(studyId);

		assertThat(notifications.ofStudy(studyId, "DISCORD_STATUS")).isEmpty();
	}

	@Test
	@DisplayName("모두 목표를 채웠으면 DISCORD_STATUS를 만들지 않는다 (pseudocode 'if any targets')")
	void noStatusPostWhenEveryoneDone() {
		register();
		rows.putMemberDay(studyId, alice, NOV_20, 1, null, null);
		clock.setInstant(seoul(NOV_20, 22, 0));

		generator.remindStudy(studyId);

		assertThat(notifications.ofStudy(studyId)).extracting(NotificationRow::type).doesNotContain("DISCORD_STATUS");
	}

}
