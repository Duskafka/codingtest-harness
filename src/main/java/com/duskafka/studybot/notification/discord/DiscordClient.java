package com.duskafka.studybot.notification.discord;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 디스코드 채널 웹훅으로 메시지를 보낸다 (S14 만들 것 2·3·4, 5.8 '디스코드', security.md 9.7). 웹훅 실행 API
 * ({@code POST /webhooks/{id}/{token}}, JSON 본문)를 쓴다.
 *
 * <ul>
 * <li>JDK {@link HttpClient}: 리다이렉트를 따라가지 않고, 연결 3초·응답 10초로 자른다 (9.7 '리다이렉트를 따라가지 않기, 응답 시간·크기
 * 제한'). 응답 본문은 429일 때만 {@value #MAX_BODY_BYTES}바이트까지 읽고 나머지는 버린다.</li>
 * <li>주소는 설정한 API 주소 + 웹훅 ID·토큰이다. 사용자가 준 호스트로는 보내지 않는다 ({@link DiscordWebhookPolicy}).</li>
 * <li>응답: 2xx 받음, 404(지워짐)·401·403(토큰 무효·권한 없음)은 더 쓸 수 없음, 429는 {@code Retry-After} 헤더(초), 없으면 본문 {@code retry_after}(초)만큼 뒤에 다시, 5xx·연결
 * 실패는 나중에 다시(발송기 백오프), 그 밖(리다이렉트, 400 등 4xx)은 다시 보내도 같아 그만둔다.</li>
 * </ul>
 *
 * <p>로그에는 웹훅 ID와 상태 코드만 남긴다. 토큰·주소·본문은 남기지 않는다.
 */
@Slf4j
public class DiscordClient {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

	static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	/** 429 대기 시간의 하한·상한. 상한보다 길면 그만큼만 기다리고, 그 전에 만료되면 발송기가 만료로 둔다. */
	static final Duration MIN_RETRY_AFTER = Duration.ofSeconds(1);

	static final Duration MAX_RETRY_AFTER = Duration.ofHours(1);

	/** 429 본문을 읽는 최대 바이트. 디스코드 응답은 100바이트 안팎이다(추측). */
	static final int MAX_BODY_BYTES = 8 * 1024;

	private final HttpClient http;

	private final String apiBaseUrl;

	private final JsonMapper jsonMapper;

	DiscordClient(HttpClient http, URI apiBaseUrl, JsonMapper jsonMapper) {
		this.http = http;
		String base = apiBaseUrl.toString();
		this.apiBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		this.jsonMapper = jsonMapper;
	}

	static HttpClient httpClient() {
		return HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.followRedirects(HttpClient.Redirect.NEVER)
			.proxy(ProxySelector.getDefault())
			.build();
	}

	/** 메시지 하나를 보낸다. 예외를 던지지 않고 결과로 돌려준다. */
	public DiscordPostResult post(DiscordWebhook webhook, DiscordMessage message) {
		HttpRequest request = HttpRequest
			.newBuilder(URI.create(this.apiBaseUrl + "/webhooks/" + webhook.id() + "/" + webhook.token()))
			.timeout(READ_TIMEOUT)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(message.toJson(this.jsonMapper), StandardCharsets.UTF_8))
			.build();
		HttpResponse<InputStream> response;
		try {
			response = this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		}
		catch (IOException ex) {
			log.warn("디스코드에 보내지 못했어요: webhook={}, error={}", webhook.id(), ex.getClass().getSimpleName());
			return new DiscordPostResult.Retry(null, "IO_ERROR");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return new DiscordPostResult.Retry(null, "INTERRUPTED");
		}
		try {
			return resultOf(webhook, response);
		}
		finally {
			closeQuietly(response.body());
		}
	}

	private DiscordPostResult resultOf(DiscordWebhook webhook, HttpResponse<InputStream> response) {
		int status = response.statusCode();
		if (status >= 200 && status < 300) {
			return new DiscordPostResult.Delivered();
		}
		log.info("디스코드가 거절했어요: webhook={}, status={}", webhook.id(), status);
		if (status == 404 || status == 401 || status == 403) {
			return new DiscordPostResult.Gone("HTTP_" + status);
		}
		if (status == 429) {
			Duration retryAfter = retryAfterHeader(response).or(() -> retryAfterBody(response.body()))
				.map(DiscordClient::clamp)
				.orElse(null);
			return new DiscordPostResult.Retry(retryAfter, "HTTP_429");
		}
		if (status >= 500) {
			return new DiscordPostResult.Retry(null, "HTTP_" + status);
		}
		return new DiscordPostResult.Rejected("HTTP_" + status);
	}

	private static void closeQuietly(InputStream body) {
		try {
			body.close();
		}
		catch (IOException ex) {
			// 결과는 이미 정했다. 닫지 못해도 연결은 HttpClient가 정리한다.
		}
	}

	/** {@code Retry-After} 헤더(초, 소수 가능). */
	static Optional<Duration> retryAfterHeader(HttpResponse<?> response) {
		return response.headers().firstValue("Retry-After").flatMap(value -> seconds(value.strip()));
	}

	/** 본문의 {@code retry_after}(초, 소수). 읽지 못하면 빈 값이다. */
	private Optional<Duration> retryAfterBody(InputStream body) {
		try {
			byte[] bytes = body.readNBytes(MAX_BODY_BYTES);
			JsonNode node = this.jsonMapper.readTree(bytes);
			JsonNode value = node == null ? null : node.get("retry_after");
			if (value == null || !value.isNumber()) {
				return Optional.empty();
			}
			return seconds(value.decimalValue());
		}
		catch (IOException | JacksonException ex) {
			return Optional.empty();
		}
	}

	private static Optional<Duration> seconds(String value) {
		if (value.isEmpty() || value.length() > 16) {
			return Optional.empty();
		}
		try {
			return seconds(new BigDecimal(value));
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}

	/** 초(소수 가능)를 밀리초로 올림한다. 음수면 빈 값이다. 아주 큰 값은 상한으로 자른다. */
	private static Optional<Duration> seconds(BigDecimal seconds) {
		if (seconds.signum() < 0) {
			return Optional.empty();
		}
		BigDecimal millis = seconds.movePointRight(3).setScale(0, RoundingMode.CEILING)
			.min(BigDecimal.valueOf(MAX_RETRY_AFTER.toMillis()));
		return Optional.of(Duration.ofMillis(millis.longValue()));
	}

	private static Duration clamp(Duration duration) {
		if (duration.compareTo(MIN_RETRY_AFTER) < 0) {
			return MIN_RETRY_AFTER;
		}
		return duration.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : duration;
	}

}
