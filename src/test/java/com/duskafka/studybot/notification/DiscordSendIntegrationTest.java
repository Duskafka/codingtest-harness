package com.duskafka.studybot.notification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.notification.DiscordStub.Webhook;
import com.duskafka.studybot.notification.NotificationRows.ChannelRow;
import com.duskafka.studybot.notification.NotificationRows.NotificationRow;
import com.duskafka.studybot.notification.discord.DiscordChannels;
import com.duskafka.studybot.notification.dispatch.NotificationDispatcher;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 디스코드 채널 발송 (S14 만들 것 3·4, 수용 기준 T18·'allowed_mentions'·'WireMock', 5.8 '디스코드', 5.9).
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 목표 1문제, 1기 11-01~11-28 ({@link StudyDayRows#standardStudy}). 알림은 발송기
 * ({@link NotificationDispatcher})가 보낸다. 발송기는 DB 전체에서 행을 고르므로 다른 테스트의 행을 먼저 만료로 바꾼다.
 */
@IntegrationTest
class DiscordSendIntegrationTest {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private static final Instant NOW = seoul(NOV_20, 22, 0);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private NotificationDispatcher dispatcher;

	@Autowired
	private DiscordChannels discordChannels;

	private StudyDayRows rows;

	private NotificationRows notifications;

	private TestApi api;

	private long studyId;

	private long owner;

	private long alice;

	private long bob;

	private long bobMembership;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		notifications = new NotificationRows(jdbc);
		api = new TestApi(mvc, jdbc);
		notifications.expireLeftovers();
		alice = rows.newUser("s14-send-alice");
		bob = rows.newUser("s14-send-bob");
		TestStudy study = rows.standardStudy(alice);
		studyId = study.studyId();
		bobMembership = rows.join(studyId, bob, NOV_1);
		owner = notifications.ownerOf(studyId);
		rows.joinAsOwner(studyId, owner, NOV_1);
		clock.setInstant(NOW);
	}

	private Webhook registered() {
		Webhook webhook = DiscordStub.newWebhook();
		assertThat(api.putJson(owner, "/api/studies/{id}/channels/discord",
				"{\"webhookUrl\":\"" + webhook.officialUrl() + "\"}", studyId))
			.hasStatusOk();
		return webhook;
	}

	private long statusRow() {
		return notifications.insertDiscordPending("DISCORD_STATUS", studyId, NOV_20,
				"{\"type\":\"DISCORD_STATUS\",\"title\":\"S14\",\"body\":\"11/20 인증 현황\",\"url\":\"/studies/" + studyId
						+ "/today\"}",
				NOW, seoul(NOV_21, 0, 0));
	}

	private JsonNode onlyRequestBody(Webhook webhook) {
		assertThat(DiscordStub.requestsTo(webhook)).hasSize(1);
		return JSON.readTree(DiscordStub.requestsTo(webhook).getFirst().getBodyAsString());
	}

	@Test
	@DisplayName("현황 게시: 임베드에 완료·남음 수, 남은 멤버 이름, 마감, 현황 링크가 들어가고 벌금 금액은 없다. 보내면 SENT")
	void statusPostHasRosterAndNoFine() {
		Webhook webhook = registered();
		DiscordStub.reply(webhook, 204);
		rows.putMemberDay(studyId, alice, NOV_20, 1, null, null);
		rows.putMemberDay(studyId, owner, NOV_20, 1, null, null);
		jdbc.sql("update studybot.study_rules set penalty_type = 'FINE', fine_amount = 4321 where study_id = :id")
			.param("id", studyId)
			.update();
		long id = statusRow();

		dispatcher.run();

		NotificationRow row = notifications.byId(id);
		assertThat(row.status()).isEqualTo("SENT");
		assertThat(row.attempts()).isEqualTo(1);
		JsonNode embed = onlyRequestBody(webhook).path("embeds").get(0);
		assertThat(embed.path("title").asString()).contains("11/20").contains("인증 현황");
		assertThat(embed.path("description").asString()).contains("완료 2명")
			.contains("남음 1명")
			.contains(notifications.loginOf(bob))
			.doesNotContain(notifications.loginOf(alice))
			.contains("마감 24:00");
		assertThat(embed.path("url").asString())
			.isEqualTo("https://studybot.example.invalid/studies/" + studyId + "/today");
		assertThat(embed.toString()).doesNotContain("4321").doesNotContain("원");
	}

	@Test
	@DisplayName("게시 메시지의 allowed_mentions가 비어 있어 스터디 이름의 @everyone이 동작하지 않는다")
	void allowedMentionsIsEmpty() {
		notifications.setStudyName(studyId, "@everyone @here 스터디");
		Webhook webhook = registered();
		DiscordStub.reply(webhook, 204);
		statusRow();

		dispatcher.run();

		JsonNode body = onlyRequestBody(webhook);
		assertThat(body.has("allowed_mentions")).isTrue();
		assertThat(body.path("allowed_mentions").path("parse").isArray()).isTrue();
		assertThat(body.path("allowed_mentions").path("parse")).isEmpty();
		assertThat(body.path("allowed_mentions").has("users")).isFalse();
		assertThat(body.path("allowed_mentions").has("roles")).isFalse();
	}

	@Test
	@DisplayName("T18: 디스코드가 429를 주면 Retry-After만큼 기다렸다 다시 보낸다 (그 전에는 보내지 않음)")
	void t18RateLimitedWaitsThenRetries() {
		Webhook webhook = registered();
		DiscordStub.replyRateLimited(webhook, "7", 6.5);
		long id = statusRow();

		dispatcher.run();

		NotificationRow waiting = notifications.byId(id);
		assertThat(waiting.status()).isEqualTo("PENDING");
		assertThat(waiting.attempts()).isEqualTo(1);
		assertThat(waiting.lastError()).isEqualTo("HTTP_429");
		assertThat(waiting.nextAttemptAt()).isEqualTo(NOW.plusSeconds(7));

		clock.setInstant(NOW.plusSeconds(6));
		dispatcher.run();
		assertThat(DiscordStub.requestsTo(webhook)).as("기다리는 동안은 보내지 않는다").hasSize(1);

		DiscordStub.reply(webhook, 204);
		clock.setInstant(NOW.plusSeconds(7));
		dispatcher.run();

		assertThat(notifications.byId(id).status()).isEqualTo("SENT");
		assertThat(DiscordStub.requestsTo(webhook)).hasSize(2);
	}

	@Test
	@DisplayName("T18: Retry-After 헤더가 없으면 응답 본문의 retry_after(초)만큼 기다린다")
	void t18RateLimitedBodyOnly() {
		Webhook webhook = registered();
		DiscordStub.replyRateLimitedBodyOnly(webhook, 2.5);
		long id = statusRow();

		dispatcher.run();

		NotificationRow waiting = notifications.byId(id);
		assertThat(waiting.status()).isEqualTo("PENDING");
		assertThat(waiting.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMillis(2500)));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(ints = { 404, 401, 403 })
	@DisplayName("T18: 디스코드가 404(지워짐)·401·403(쓸 수 없음)을 주면 웹훅을 끄고(마지막 오류) 알림은 FAILED, 방장에게 웹 푸시 알림(DISCORD_DISABLED)을 만든다 (401·403은 사람이 정함 2026-11-28)")
	void t18GoneDisablesAndNotifiesOwner(int status) {
		Webhook webhook = registered();
		DiscordStub.reply(webhook, status);
		long revision = notifications.channel(studyId).orElseThrow().revision();
		long id = statusRow();

		dispatcher.run();

		NotificationRow row = notifications.byId(id);
		assertThat(row.status()).isEqualTo("FAILED");
		assertThat(row.lastError()).isEqualTo("HTTP_" + status);
		ChannelRow channel = notifications.channel(studyId).orElseThrow();
		assertThat(channel.enabled()).isFalse();
		assertThat(channel.lastError()).isEqualTo("HTTP_" + status);
		NotificationRow notice = notifications.ofStudy(studyId, "DISCORD_DISABLED").getFirst();
		assertThat(notice.channel()).isEqualTo("WEB_PUSH");
		assertThat(notice.userId()).isEqualTo(owner);
		assertThat(notice.status()).isEqualTo("PENDING");
		assertThat(notice.dedupeKey()).isEqualTo("DISCORD_DISABLED:" + studyId + ":" + revision);
		assertThat(notice.payload()).doesNotContain(webhook.token()).doesNotContain(webhook.id());
	}

	@Test
	@DisplayName("404를 받기 전에 방장이 웹훅을 다시 등록했으면(리비전이 바뀜) 새 웹훅을 끄지 않고 방장 알림도 만들지 않는다")
	void staleGoneDoesNotDisableNewWebhook() {
		registered();
		long oldRevision = notifications.channel(studyId).orElseThrow().revision();
		registered();

		boolean disabled = discordChannels.markGone(studyId, oldRevision, "HTTP_404");

		assertThat(disabled).isFalse();
		ChannelRow channel = notifications.channel(studyId).orElseThrow();
		assertThat(channel.enabled()).isTrue();
		assertThat(channel.lastError()).isNull();
		assertThat(notifications.ofStudy(studyId, "DISCORD_DISABLED")).isEmpty();
	}

	@Test
	@DisplayName("보내기 직전에 디스코드가 꺼져 있으면(해제·404로 꺼짐) 보내지 않고 SKIPPED")
	void disabledChannelIsSkipped() {
		Webhook webhook = registered();
		DiscordStub.reply(webhook, 204);
		long id = statusRow();
		assertThat(api.putJson(owner, "/api/studies/{id}/channels/discord", "{\"webhookUrl\":null}", studyId))
			.hasStatusOk();

		dispatcher.run();

		assertThat(notifications.byId(id).status()).isEqualTo("SKIPPED");
		assertThat(DiscordStub.requestsTo(webhook)).isEmpty();
	}

	@Test
	@DisplayName("5xx는 일시 실패라 발송기 백오프(30초)로 다시 보낸다. 웹훅은 켜진 채다")
	void serverErrorBacksOff() {
		Webhook webhook = registered();
		DiscordStub.reply(webhook, 503);
		long id = statusRow();

		dispatcher.run();

		NotificationRow row = notifications.byId(id);
		assertThat(row.status()).isEqualTo("PENDING");
		assertThat(row.lastError()).isEqualTo("HTTP_503");
		assertThat(row.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
		assertThat(notifications.channel(studyId).orElseThrow().enabled()).isTrue();
	}

	@Test
	@DisplayName("리다이렉트를 따라가지 않는다 (9.7): 301은 FAILED이고 Location으로 요청하지 않는다")
	void redirectIsNotFollowed() {
		Webhook webhook = registered();
		Webhook target = DiscordStub.newWebhook();
		DiscordStub.reply(target, 204);
		DiscordStub.replyRedirect(webhook, 301, DiscordStub.apiBaseUrl() + "/webhooks/" + target.id() + "/" + target.token());
		long id = statusRow();

		dispatcher.run();

		NotificationRow row = notifications.byId(id);
		assertThat(row.status()).isEqualTo("FAILED");
		assertThat(row.lastError()).isEqualTo("HTTP_301");
		assertThat(DiscordStub.requestsTo(target)).isEmpty();
	}

	@Test
	@DisplayName("결과 게시: 판정 결과의 성공·실패·면제 수와 실패한 멤버 이름이 들어가고 벌금 금액은 없다")
	void dailyResultPostListsFailures() {
		Webhook webhook = registered();
		DiscordStub.reply(webhook, 204);
		rows.markJudged(studyId, alice, NOV_20, "SUCCESS");
		rows.markJudged(studyId, bob, NOV_20, "FAIL");
		rows.markJudged(studyId, owner, NOV_20, "EXEMPT_TICKET");
		clock.setInstant(seoul(NOV_21, 0, 45));
		long id = notifications.insertDiscordPending("DAILY_RESULT", studyId, NOV_20,
				"{\"type\":\"DAILY_RESULT\",\"title\":\"S14\",\"body\":\"11/20 결과\",\"url\":\"/studies/" + studyId
						+ "/days/2026-11-20\"}",
				seoul(NOV_21, 0, 45), seoul(LocalDate.of(2026, 11, 22), 0, 0));

		dispatcher.run();

		assertThat(notifications.byId(id).status()).isEqualTo("SENT");
		JsonNode body = onlyRequestBody(webhook);
		assertThat(body.path("allowed_mentions").path("parse")).isEmpty();
		JsonNode embed = body.path("embeds").get(0);
		assertThat(embed.path("title").asString()).contains("11/20").contains("결과");
		assertThat(embed.path("description").asString()).contains("성공 1명")
			.contains("실패 1명")
			.contains("면제 1명")
			.contains(notifications.loginOf(bob))
			.doesNotContain(notifications.loginOf(alice));
		assertThat(embed.path("url").asString())
			.isEqualTo("https://studybot.example.invalid/studies/" + studyId + "/days/2026-11-20");
	}


	private static final String RESULT_PAYLOAD = "{\"type\":\"DAILY_RESULT\",\"title\":\"S09 스터디\",\"body\":\"11/20 결과: 실패 1명\",\"url\":\"/studies/%d/days/2026-11-20\"}";

	private long resultRow() {
		clock.setInstant(seoul(NOV_21, 0, 45));
		return notifications.insertDiscordPending("DAILY_RESULT", studyId, NOV_20, RESULT_PAYLOAD.formatted(studyId),
				seoul(NOV_21, 0, 45), seoul(LocalDate.of(2026, 11, 22), 0, 0));
	}

	private List<NotificationRow> ownerResultFallbacks() {
		return notifications.ofStudy(studyId, "DAILY_RESULT")
			.stream()
			.filter(row -> row.channel().equals("WEB_PUSH"))
			.toList();
	}

	@Test
	@DisplayName("현황 게시: 지금 탈퇴한 멤버(그날 다시 가입하지 않음)는 완료·남음 수와 이름에서 뺀다 (사람이 정함 2026-11-28)")
	void statusPostExcludesMemberWhoLeft() {
		Webhook webhook = registered();
		DiscordStub.reply(webhook, 204);
		rows.putMemberDay(studyId, alice, NOV_20, 1, null, null);
		rows.putMemberDay(studyId, owner, NOV_20, 1, null, null);
		rows.leave(bobMembership, seoul(NOV_20, 9, 0));
		statusRow();

		dispatcher.run();

		String description = onlyRequestBody(webhook).path("embeds").get(0).path("description").asString();
		assertThat(description).contains("완료 2명").contains("남음 0명").doesNotContain(notifications.loginOf(bob));
	}

	@Test
	@DisplayName("결과 게시: 그날 탈퇴한 멤버도 그날까지 판정하므로(R14) 실패 이름에 남는다")
	void dailyResultPostKeepsMemberWhoLeft() {
		Webhook webhook = registered();
		DiscordStub.reply(webhook, 204);
		rows.leave(bobMembership, seoul(NOV_20, 9, 0));
		rows.markJudged(studyId, alice, NOV_20, "SUCCESS");
		rows.markJudged(studyId, bob, NOV_20, "FAIL");
		resultRow();

		dispatcher.run();

		assertThat(onlyRequestBody(webhook).path("embeds").get(0).path("description").asString())
			.contains("실패 1명")
			.contains(notifications.loginOf(bob));
	}

	@Test
	@DisplayName("디스코드 결과 알림을 보내기 전에 방장이 디스코드를 해제했으면 SKIPPED로 두고 같은 내용을 방장 웹 푸시로 대신 넣는다 (사람이 정함 2026-11-28)")
	void dailyResultFallsBackToOwnerWhenUnregistered() {
		Webhook webhook = registered();
		DiscordStub.reply(webhook, 204);
		long id = resultRow();
		assertThat(api.putJson(owner, "/api/studies/{id}/channels/discord", "{\"webhookUrl\":null}", studyId))
			.hasStatusOk();

		dispatcher.run();

		assertThat(notifications.byId(id).status()).isEqualTo("SKIPPED");
		assertThat(DiscordStub.requestsTo(webhook)).isEmpty();
		assertThat(ownerResultFallbacks()).singleElement().satisfies(row -> {
			assertThat(row.userId()).isEqualTo(owner);
			assertThat(row.studyDay()).isEqualTo(NOV_20);
			assertThat(row.dedupeKey()).isEqualTo("DAILY_RESULT:" + studyId + ":" + NOV_20 + ":owner");
			assertThat(row.status()).isEqualTo("PENDING");
			assertThat(row.expiresAt()).isEqualTo(seoul(LocalDate.of(2026, 11, 22), 0, 0));
			assertThat(JSON.readTree(row.payload())).isEqualTo(JSON.readTree(RESULT_PAYLOAD.formatted(studyId)));
		});

		dispatcher.run();
		assertThat(ownerResultFallbacks()).as("대신 보낸 알림은 하나").hasSize(1);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(ints = { 404, 401, 403 })
	@DisplayName("디스코드 결과 알림이 404·401·403을 받으면 웹훅을 끄고 FAILED, 방장에게 꺼짐 알림과 결과 알림 웹 푸시를 넣는다 (사람이 정함 2026-11-28)")
	void dailyResultFallsBackToOwnerWhenGone(int status) {
		Webhook webhook = registered();
		DiscordStub.reply(webhook, status);
		long id = resultRow();

		dispatcher.run();

		assertThat(notifications.byId(id).status()).isEqualTo("FAILED");
		assertThat(notifications.ofStudy(studyId, "DISCORD_DISABLED")).hasSize(1);
		assertThat(ownerResultFallbacks()).singleElement().satisfies(row -> {
			assertThat(row.userId()).isEqualTo(owner);
			assertThat(row.dedupeKey()).isEqualTo("DAILY_RESULT:" + studyId + ":" + NOV_20 + ":owner");
		});
	}

	@Test
	@DisplayName("디스코드 결과 알림이 그 밖의 실패(5xx 재시도, 400)면 방장 웹 푸시로 대신 넣지 않는다")
	void dailyResultDoesNotFallBackOnOtherFailures() {
		Webhook webhook = registered();
		DiscordStub.reply(webhook, 400);
		long id = resultRow();

		dispatcher.run();

		assertThat(notifications.byId(id).status()).isEqualTo("FAILED");
		assertThat(ownerResultFallbacks()).isEmpty();
	}

	@Test
	@DisplayName("현황 게시(DISCORD_STATUS)는 꺼진 채널이어도 방장 웹 푸시로 대신 넣지 않는다 (방장에게는 공유 유도가 따로 감)")
	void statusPostDoesNotFallBack() {
		registered();
		long id = statusRow();
		assertThat(api.putJson(owner, "/api/studies/{id}/channels/discord", "{\"webhookUrl\":null}", studyId))
			.hasStatusOk();

		dispatcher.run();

		assertThat(notifications.byId(id).status()).isEqualTo("SKIPPED");
		assertThat(notifications.ofStudy(studyId)).extracting(NotificationRow::channel).containsOnly("DISCORD");
	}

}
