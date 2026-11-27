package com.duskafka.studybot.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.UUID;

/**
 * 브라우저 제조사의 웹 푸시 서비스를 흉내 낸다 (S13 수용 기준 '웹 푸시 서비스는 WireMock으로', external-systems.md). 테스트는 실제 푸시
 * 서비스에 접속하지 않는다.
 *
 * <p>구독 주소마다 응답을 정한다. 정하지 않은 주소는 WireMock 기본 404라 구독이 지워진다. 서버는 JVM에 하나만 띄우고 끄지 않는다
 * ({@code GithubApiStub}과 같은 방식). 구독 주소는 테스트가 DB에 바로 넣는다. 허용 푸시 도메인 검사는 등록 API에서만 하기 때문이다
 * (S13 PR 판단 기록).
 */
public final class WebPushStub {

	private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());

	private WebPushStub() {
	}

	public static synchronized WireMockServer server() {
		if (!SERVER.isRunning()) {
			SERVER.start();
		}
		return SERVER;
	}

	/** 새 구독 주소. 경로 끝이 겹치지 않는다. */
	public static String newEndpoint() {
		return server().baseUrl() + "/push/" + UUID.randomUUID();
	}

	/** 구독 주소의 출처 (VAPID {@code aud}). */
	public static String origin() {
		return server().baseUrl();
	}

	/** 이 구독 주소로 오는 요청에 이 상태 코드로 답한다. */
	public static void reply(String endpoint, int status) {
		reply(endpoint, aResponse().withStatus(status));
	}

	/** 이 구독 주소로 오는 요청에 이 상태 코드와 {@code Retry-After}로 답한다. */
	public static void replyWithRetryAfter(String endpoint, int status, String retryAfter) {
		reply(endpoint, aResponse().withStatus(status).withHeader("Retry-After", retryAfter));
	}

	private static void reply(String endpoint, ResponseDefinitionBuilder response) {
		server().stubFor(post(urlPathEqualTo(pathOf(endpoint))).willReturn(response));
	}

	/** 이 구독 주소로 온 요청들 (받은 순서). */
	public static List<LoggedRequest> requestsTo(String endpoint) {
		return server().findAll(postRequestedFor(urlPathEqualTo(pathOf(endpoint))));
	}

	private static String pathOf(String endpoint) {
		return endpoint.substring(server().baseUrl().length());
	}

}
