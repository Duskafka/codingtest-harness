package com.duskafka.studybot.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 디스코드 채널 웹훅을 흉내 낸다 (S14 수용 기준 '디스코드는 WireMock으로 테스트한다'). 테스트는 실제 디스코드에 접속하지 않는다.
 *
 * <p>앱은 등록 때 공식 주소({@code https://discord.com/api/webhooks/…})만 받고, 보낼 때는 설정한 API 주소
 * ({@code studybot.discord.api-base-url})에 웹훅 ID·토큰 경로를 붙인다. 테스트 설정은 그 주소를 이 서버로 한다
 * ({@code DiscordTestConfiguration}). 웹훅마다 응답을 정하고, 정하지 않은 웹훅은 WireMock 기본 404다. 서버는 JVM에 하나만 띄우고 끄지
 * 않는다 ({@link WebPushStub}과 같은 방식).
 */
public final class DiscordStub {

	private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

	private static final AtomicLong NEXT_ID = new AtomicLong(1_300_000_000_000_000_000L);

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";

	private DiscordStub() {
	}

	public static synchronized WireMockServer server() {
		if (!SERVER.isRunning()) {
			SERVER.start();
		}
		return SERVER;
	}

	/** 앱 설정의 디스코드 API 주소 (공식 주소 {@code https://discord.com/api} 자리). */
	public static String apiBaseUrl() {
		return server().baseUrl() + "/api";
	}

	/** 새 웹훅. ID·토큰이 겹치지 않는다. 토큰은 디스코드처럼 68자다 (형식은 추측, 공식 문서는 길이를 정하지 않음). */
	public static Webhook newWebhook() {
		StringBuilder token = new StringBuilder();
		for (int i = 0; i < 68; i++) {
			token.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
		}
		return new Webhook(Long.toString(NEXT_ID.incrementAndGet()), token.toString());
	}

	/** 이 웹훅으로 오는 요청에 이 상태 코드로 답한다. */
	public static void reply(Webhook webhook, int status) {
		reply(webhook, aResponse().withStatus(status));
	}

	/** 429와 {@code Retry-After} 헤더(초)로 답한다. 본문에도 디스코드처럼 {@code retry_after}를 넣는다. */
	public static void replyRateLimited(Webhook webhook, String retryAfterHeader, double retryAfterBody) {
		reply(webhook, aResponse().withStatus(429)
			.withHeader("Retry-After", retryAfterHeader)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"message\":\"You are being rate limited.\",\"retry_after\":" + retryAfterBody
					+ ",\"global\":false}"));
	}

	/** 429에 {@code Retry-After} 헤더 없이 본문의 {@code retry_after}(초, 소수)만 준다. */
	public static void replyRateLimitedBodyOnly(Webhook webhook, double retryAfterBody) {
		reply(webhook, aResponse().withStatus(429)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"message\":\"You are being rate limited.\",\"retry_after\":" + retryAfterBody
					+ ",\"global\":false}"));
	}

	/** 리다이렉트(이 상태 코드와 {@code Location})로 답한다. */
	public static void replyRedirect(Webhook webhook, int status, String location) {
		reply(webhook, aResponse().withStatus(status).withHeader("Location", location));
	}

	private static void reply(Webhook webhook, ResponseDefinitionBuilder response) {
		server().stubFor(post(urlPathEqualTo(webhook.path())).willReturn(response));
	}

	/** 이 웹훅으로 온 요청들 (받은 순서). */
	public static List<LoggedRequest> requestsTo(Webhook webhook) {
		return server().findAll(postRequestedFor(urlPathEqualTo(webhook.path())));
	}

	/**
	 * 디스코드 웹훅 하나.
	 *
	 * @param id 웹훅 ID (숫자)
	 * @param token 웹훅 토큰
	 */
	public record Webhook(String id, String token) {

		/** 사용자가 디스코드에서 복사하는 공식 주소. */
		public String officialUrl() {
			return "https://discord.com/api/webhooks/" + this.id + "/" + this.token;
		}

		/** 이 서버에서 받는 경로. */
		public String path() {
			return "/api/webhooks/" + this.id + "/" + this.token;
		}

	}

}
