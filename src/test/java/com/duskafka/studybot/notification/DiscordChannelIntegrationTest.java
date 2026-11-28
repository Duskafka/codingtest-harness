package com.duskafka.studybot.notification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.notification.DiscordStub.Webhook;
import com.duskafka.studybot.notification.NotificationRows.ChannelRow;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 디스코드 웹훅 등록·해제·테스트 게시 (S14 만들 것 1·2, 수용 기준 T27·'평문으로 남지 않음'·T19, security.md 9.7 '보내는 요청').
 *
 * <p>디스코드는 WireMock이다 ({@link DiscordStub}). 등록은 공식 주소만 받고, 보낼 때는 테스트 설정의 API 주소로 보낸다.
 */
@IntegrationTest
class DiscordChannelIntegrationTest {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	private StudyDayRows rows;

	private NotificationRows notifications;

	private TestApi api;

	private long studyId;

	private long owner;

	private long member;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		notifications = new NotificationRows(jdbc);
		api = new TestApi(mvc, jdbc);
		member = rows.newUser("s14-ch-member");
		TestStudy study = rows.standardStudy(member);
		studyId = study.studyId();
		owner = notifications.ownerOf(studyId);
		rows.joinAsOwner(studyId, owner, NOV_1);
		clock.setInstant(seoul(NOV_20, 21, 0));
	}

	private MvcTestResult register(long userId, long study, String webhookUrl) {
		return api.putJson(userId, "/api/studies/{id}/channels/discord",
				"{\"webhookUrl\":" + JSON.writeValueAsString(webhookUrl) + "}", study);
	}

	private MvcTestResult unregister(long userId, long study) {
		return api.putJson(userId, "/api/studies/{id}/channels/discord", "{\"webhookUrl\":null}", study);
	}

	private MvcTestResult testPost(long userId, long study) {
		return api.postJson(userId, "/api/studies/{id}/channels/discord/test", "", study);
	}

	private static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Test
	@DisplayName("공식 웹훅 주소를 등록하면 200과 상태만 준다. 응답과 DB 어디에도 웹훅 ID·토큰이 평문으로 남지 않는다")
	void registersOfficialUrlWithoutPlaintext() {
		Webhook webhook = DiscordStub.newWebhook();

		MvcTestResult result = register(owner, studyId, webhook.officialUrl());

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode json = TestApi.json(result);
		assertThat(json.path("registered").asBoolean()).isTrue();
		assertThat(json.path("enabled").asBoolean()).isTrue();
		assertThat(json.path("lastError").isNull()).isTrue();
		assertThat(body(result)).doesNotContain(webhook.token()).doesNotContain(webhook.id()).doesNotContain("discord.com");
		ChannelRow channel = notifications.channel(studyId).orElseThrow();
		assertThat(channel.enabled()).isTrue();
		assertThat(channel.revision()).isEqualTo(1);
		assertThat(channel.ciphertext()).startsWith("v1:")
			.doesNotContain(webhook.token())
			.doesNotContain(webhook.id())
			.doesNotContain("discord");
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "https://discordapp.com/api/webhooks/%s/%s", "https://ptb.discord.com/api/webhooks/%s/%s",
			"https://canary.discord.com/api/webhooks/%s/%s", "https://discord.com/api/v10/webhooks/%s/%s",
			"https://DISCORD.com/api/webhooks/%s/%s", "  https://discord.com/api/webhooks/%s/%s  " })
	@DisplayName("디스코드 공식 도메인·API 버전 경로·대문자 호스트·앞뒤 공백도 받고, 보낼 때는 웹훅 ID·토큰만 써서 공식 API 경로로 보낸다")
	void acceptsOfficialVariantsAndPostsToCanonicalPath(String template) {
		Webhook webhook = DiscordStub.newWebhook();
		DiscordStub.reply(webhook, 204);

		assertThat(register(owner, studyId, template.formatted(webhook.id(), webhook.token()))).hasStatus(HttpStatus.OK);
		assertThat(testPost(owner, studyId)).hasStatus(HttpStatus.OK);

		assertThat(DiscordStub.requestsTo(webhook)).hasSize(1);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "http://discord.com/api/webhooks/%s/%s", "https://169.254.169.254/api/webhooks/%s/%s",
			"https://localhost/api/webhooks/%s/%s", "https://127.0.0.1/api/webhooks/%s/%s",
			"https://[::1]/api/webhooks/%s/%s", "https://10.0.0.1/api/webhooks/%s/%s",
			"https://evil.example.com/api/webhooks/%s/%s", "https://discord.com.evil.example/api/webhooks/%s/%s",
			"https://evildiscord.com/api/webhooks/%s/%s", "https://discord.com@evil.example/api/webhooks/%s/%s",
			"https://user@discord.com/api/webhooks/%s/%s", "https://discord.com:8443/api/webhooks/%s/%s",
			"https://discord.com/api/webhooks/%s/%s?wait=true", "https://discord.com/api/webhooks/%s/%s#frag",
			"https://discord.com/api/webhooks/%s/%s/slack", "https://discord.com/api/webhooks/%s/%s/../x",
			"https://discord.com/api/webhooks/abc%s/%s", "https://discord.com/api/webhooks/%s",
			"https://discord.com/api/oauth2/%s/%s", "ftp://discord.com/api/webhooks/%s/%s",
			"javascript:alert(1)//%s/%s", "discord.com/api/webhooks/%s/%s" })
	@DisplayName("T27: 디스코드 공식 웹훅 주소가 아닌 URL(내부 주소, 다른 도메인, 사용자 정보·포트·쿼리·다른 경로)은 400으로 거절하고 저장하지 않는다")
	void t27RejectsNonOfficialUrls(String template) {
		Webhook webhook = DiscordStub.newWebhook();
		String url = template.contains("%s/%s") ? template.formatted(webhook.id(), webhook.token())
				: template.formatted(webhook.id());

		MvcTestResult result = register(owner, studyId, url);

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(notifications.channel(studyId)).isEmpty();
	}

	@Test
	@DisplayName("T27: 빈 주소·너무 긴 주소는 400이다")
	void t27RejectsBlankAndTooLong() {
		Webhook webhook = DiscordStub.newWebhook();

		assertThat(register(owner, studyId, "")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(register(owner, studyId, webhook.officialUrl() + "a".repeat(600))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(api.putJson(owner, "/api/studies/{id}/channels/discord", "{}", studyId))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(notifications.channel(studyId)).isEmpty();
	}

	@Test
	@DisplayName("웹훅을 해제하면 저장된 주소를 지우고 꺼진다. 다시 등록하면 켜지고 마지막 오류가 지워지며 리비전이 오른다")
	void unregisterAndReRegister() {
		Webhook first = DiscordStub.newWebhook();
		DiscordStub.reply(first, 404);
		register(owner, studyId, first.officialUrl());
		testPost(owner, studyId);
		assertThat(notifications.channel(studyId).orElseThrow().lastError()).isEqualTo("HTTP_404");

		MvcTestResult removed = unregister(owner, studyId);

		assertThat(removed).hasStatus(HttpStatus.OK);
		assertThat(TestApi.json(removed).path("registered").asBoolean()).isFalse();
		assertThat(TestApi.json(removed).path("enabled").asBoolean()).isFalse();
		ChannelRow afterRemove = notifications.channel(studyId).orElseThrow();
		assertThat(afterRemove.ciphertext()).isNull();
		assertThat(afterRemove.enabled()).isFalse();

		Webhook second = DiscordStub.newWebhook();
		MvcTestResult again = register(owner, studyId, second.officialUrl());

		assertThat(TestApi.json(again).path("enabled").asBoolean()).isTrue();
		assertThat(TestApi.json(again).path("lastError").isNull()).isTrue();
		ChannelRow afterAgain = notifications.channel(studyId).orElseThrow();
		assertThat(afterAgain.revision()).isGreaterThan(afterRemove.revision());
		assertThat(afterAgain.lastError()).isNull();
	}

	@Test
	@DisplayName("혼자 쓰기 스터디는 디스코드 등록·테스트 게시를 409(SOLO_STUDY_NO_SHARING)로 거절한다 (R15, 사람이 정함 2026-11-28)")
	void soloStudyRejected() {
		notifications.setStudyMode(studyId, "SOLO");
		Webhook webhook = DiscordStub.newWebhook();

		MvcTestResult put = register(owner, studyId, webhook.officialUrl());
		MvcTestResult post = testPost(owner, studyId);

		assertThat(put).hasStatus(HttpStatus.CONFLICT);
		assertThat(TestApi.json(put).path("code").asString()).isEqualTo("SOLO_STUDY_NO_SHARING");
		assertThat(post).hasStatus(HttpStatus.CONFLICT);
		assertThat(TestApi.json(post).path("code").asString()).isEqualTo("SOLO_STUDY_NO_SHARING");
		assertThat(notifications.channel(studyId)).isEmpty();
	}

	@Test
	@DisplayName("T19: 멤버가 아니거나 다른 스터디 ID면 404, 방장이 아닌 멤버는 403, 로그인하지 않으면 401이고 아무것도 바뀌지 않는다")
	void t19OthersCannotTouchChannel() {
		long outsider = rows.newUser("s14-ch-outsider");
		long otherMember = rows.newUser("s14-ch-other");
		TestStudy other = rows.standardStudy(otherMember);
		long otherOwner = notifications.ownerOf(other.studyId());
		rows.joinAsOwner(other.studyId(), otherOwner, NOV_1);
		Webhook webhook = DiscordStub.newWebhook();
		DiscordStub.reply(webhook, 204);

		assertThat(register(outsider, studyId, webhook.officialUrl())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(register(outsider, studyId, "not a url")).as("본문 검사보다 권한이 먼저").hasStatus(HttpStatus.NOT_FOUND);
		assertThat(register(otherOwner, studyId, webhook.officialUrl())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(register(member, studyId, webhook.officialUrl())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(unregister(member, studyId)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(api.putJsonAnonymous("/api/studies/{id}/channels/discord",
				"{\"webhookUrl\":\"" + webhook.officialUrl() + "\"}", studyId))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(notifications.channel(studyId)).isEmpty();

		register(owner, studyId, webhook.officialUrl());
		assertThat(testPost(outsider, studyId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(testPost(otherOwner, studyId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(testPost(member, studyId)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(api.postJsonAnonymous("/api/studies/{id}/channels/discord/test", "", studyId))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(DiscordStub.requestsTo(webhook)).isEmpty();
	}

	@Test
	@DisplayName("테스트 게시: 디스코드가 받으면 200과 상태. 본문은 JSON 임베드이고 allowed_mentions가 비어 있다")
	void testPostDelivers() {
		notifications.setStudyName(studyId, "@everyone 스터디");
		Webhook webhook = DiscordStub.newWebhook();
		DiscordStub.reply(webhook, 204);
		register(owner, studyId, webhook.officialUrl());

		MvcTestResult result = testPost(owner, studyId);

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(TestApi.json(result).path("enabled").asBoolean()).isTrue();
		assertThat(body(result)).doesNotContain(webhook.token());
		List<LoggedRequest> requests = DiscordStub.requestsTo(webhook);
		assertThat(requests).hasSize(1);
		assertThat(requests.getFirst().getHeader("Content-Type")).startsWith("application/json");
		JsonNode sent = JSON.readTree(requests.getFirst().getBodyAsString());
		assertThat(sent.path("allowed_mentions").path("parse").isArray()).isTrue();
		assertThat(sent.path("allowed_mentions").path("parse")).isEmpty();
		assertThat(sent.path("embeds").get(0).path("title").asString()).contains("@everyone 스터디");
	}

	@Test
	@DisplayName("테스트 게시: 등록하지 않았거나 꺼져 있으면 409(DISCORD_NOT_REGISTERED)")
	void testPostWithoutWebhook() {
		MvcTestResult result = testPost(owner, studyId);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(TestApi.json(result).path("code").asString()).isEqualTo("DISCORD_NOT_REGISTERED");
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(ints = { 404, 401, 403 })
	@DisplayName("테스트 게시: 디스코드가 404(지워짐)·401·403(쓸 수 없음)이면 끄고 409(DISCORD_WEBHOOK_GONE) (사람이 정함 2026-11-28 '401·403도 404처럼')")
	void testPostGoneDisables(int status) {
		Webhook webhook = DiscordStub.newWebhook();
		DiscordStub.reply(webhook, status);
		register(owner, studyId, webhook.officialUrl());

		MvcTestResult result = testPost(owner, studyId);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(TestApi.json(result).path("code").asString()).isEqualTo("DISCORD_WEBHOOK_GONE");
		ChannelRow channel = notifications.channel(studyId).orElseThrow();
		assertThat(channel.enabled()).isFalse();
		assertThat(channel.lastError()).isEqualTo("HTTP_" + status);
		assertThat(testPost(owner, studyId)).as("꺼진 뒤에는 보내지 않는다").hasStatus(HttpStatus.CONFLICT);
		assertThat(DiscordStub.requestsTo(webhook)).hasSize(1);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(ints = { 429, 500, 503 })
	@DisplayName("테스트 게시: 429·5xx면 503(DISCORD_UNAVAILABLE)이고 웹훅은 켜진 채다")
	void testPostUnavailable(int status) {
		Webhook webhook = DiscordStub.newWebhook();
		DiscordStub.reply(webhook, status);
		register(owner, studyId, webhook.officialUrl());

		MvcTestResult result = testPost(owner, studyId);

		assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(TestApi.json(result).path("code").asString()).isEqualTo("DISCORD_UNAVAILABLE");
		assertThat(notifications.channel(studyId).orElseThrow().enabled()).isTrue();
	}

	@Test
	@DisplayName("테스트 게시: 그 밖의 4xx(400 등)는 409(DISCORD_WEBHOOK_REJECTED)이고 마지막 오류를 남긴다. 끄지는 않는다")
	void testPostRejected() {
		Webhook webhook = DiscordStub.newWebhook();
		DiscordStub.reply(webhook, 400);
		register(owner, studyId, webhook.officialUrl());

		MvcTestResult result = testPost(owner, studyId);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(TestApi.json(result).path("code").asString()).isEqualTo("DISCORD_WEBHOOK_REJECTED");
		ChannelRow channel = notifications.channel(studyId).orElseThrow();
		assertThat(channel.enabled()).isTrue();
		assertThat(channel.lastError()).isEqualTo("HTTP_400");
	}

}
