package com.duskafka.studybot.github.webhook;

import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.common.error.UnauthorizedException;
import com.duskafka.studybot.github.webhook.GithubWebhookReceiver.Receipt;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GitHub 웹훅 수신 {@code POST /webhooks/github} (S06, 5.3, api.md 4.4). 인증은 서명으로만 한다. 보안 체인은
 * {@code /webhooks/**}를 CSRF·세션에서 뺀다 (S04 {@code SecurityConfiguration}).
 *
 * <p>본문을 객체로 먼저 바꾸면 직렬화가 달라져 서명이 맞지 않는다 [S36]. 그래서 입력 스트림을 바이트로 읽어 서명부터 확인하고,
 * 그다음에 JSON으로 파싱한다. 응답은 5.3 '응답 코드 규칙'을 따른다.
 *
 * <table>
 * <caption>응답</caption>
 * <tr><th>상황</th><th>응답</th></tr>
 * <tr><td>본문이 한도보다 큼</td><td>413 (전달 ID를 경고 로그로)</td></tr>
 * <tr><td>서명이 없거나 틀림</td><td>401</td></tr>
 * <tr><td>이벤트·전달 ID 헤더가 없음, 본문이 JSON 객체가 아님</td><td>400</td></tr>
 * <tr><td>관심 없는 이벤트</td><td>204 (남기지 않음)</td></tr>
 * <tr><td>수신함에 저장하지 못함</td><td>503</td></tr>
 * <tr><td>이미 받은 전달, ping</td><td>200</td></tr>
 * <tr><td>저장 성공 (처리 성공·실패와 상관없이)</td><td>202</td></tr>
 * </table>
 */
@Slf4j
@RestController
class GithubWebhookController {

	static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

	static final String EVENT_HEADER = "X-GitHub-Event";

	static final String DELIVERY_HEADER = "X-GitHub-Delivery";

	/** 확인 안 된 헤더 값을 로그에 남길 때의 최대 글자 수. GitHub 전달 ID(GUID)는 36자다. */
	static final int LOGGED_HEADER_MAX_LENGTH = 100;

	private final GithubSignatureVerifier signatureVerifier;

	private final GithubWebhookReceiver receiver;

	private final JsonMapper jsonMapper;

	private final long maxBodyBytes;

	GithubWebhookController(GithubSignatureVerifier signatureVerifier, GithubWebhookReceiver receiver,
			JsonMapper jsonMapper, GithubWebhookProperties properties) {
		this.signatureVerifier = signatureVerifier;
		this.receiver = receiver;
		this.jsonMapper = jsonMapper;
		this.maxBodyBytes = properties.maxBodySize().toBytes();
	}

	@PostMapping("/webhooks/github")
	ResponseEntity<Void> receive(HttpServletRequest request,
			@RequestHeader(name = SIGNATURE_HEADER, required = false) @Nullable String signature,
			@RequestHeader(name = EVENT_HEADER, required = false) @Nullable String eventName,
			@RequestHeader(name = DELIVERY_HEADER, required = false) @Nullable String deliveryId) throws IOException {
		byte[] body = readBody(request, eventName, deliveryId);
		if (!signatureVerifier.verify(body, signature)) {
			throw new UnauthorizedException("웹훅 서명이 없거나 맞지 않아요");
		}
		if (eventName == null || eventName.isBlank()) {
			throw new BadRequestException(EVENT_HEADER + " 헤더가 없어요");
		}
		Optional<GithubEvent> event = GithubEvent.fromHeader(eventName);
		if (event.isEmpty()) {
			return ResponseEntity.noContent().build();
		}
		if (deliveryId == null || deliveryId.isBlank()) {
			throw new BadRequestException(DELIVERY_HEADER + " 헤더가 없어요");
		}
		JsonNode payload = parseObject(body);

		Receipt receipt = receiver.receive(event.get(), deliveryId, payload, new String(body, StandardCharsets.UTF_8));

		if (receipt == Receipt.DUPLICATE || event.get() == GithubEvent.PING) {
			return ResponseEntity.ok().build();
		}
		return ResponseEntity.accepted().build();
	}

	/**
	 * 본문을 한도까지 읽는다. 한도를 넘으면 413이고, 전달 ID를 경고 로그로 남긴다 (사람이 정함, 2026-11-23 S06 세션
	 * "5MB 유지 + 경고 로그"). GitHub는 실패한 전달을 스스로 다시 보내지 않으니 [S34], 운영자가 로그를 보고 한도를 올린 뒤
	 * GitHub App 설정의 'Recent Deliveries'에서 3일 안에 재전송한다 [S35]. 서명을 확인하기 전이라 헤더 값은 믿지 않고 다듬어서 남긴다.
	 */
	private byte[] readBody(HttpServletRequest request, @Nullable String eventName, @Nullable String deliveryId)
			throws IOException {
		long contentLength = request.getContentLengthLong();
		try {
			return WebhookBodyReader.read(request.getInputStream(), contentLength, maxBodyBytes);
		}
		catch (WebhookBodyTooLargeException ex) {
			log.warn("웹훅 본문이 한도를 넘어 413으로 거절했어요 (서명 확인 전, 확인 안 된 헤더): delivery={}, event={}, "
					+ "contentLength={}, limit={}", loggable(deliveryId), loggable(eventName), contentLength, maxBodyBytes);
			throw ex;
		}
	}

	/** 확인 안 된 헤더 값을 로그에 남길 모양으로: 제어 문자(줄바꿈 등)는 {@code _}로 바꾸고 {@value #LOGGED_HEADER_MAX_LENGTH}자에서 자른다. */
	private static String loggable(@Nullable String headerValue) {
		if (headerValue == null) {
			return "(없음)";
		}
		String cleaned = headerValue.codePoints()
			.map(cp -> Character.isISOControl(cp) ? '_' : cp)
			.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
			.toString();
		return cleaned.length() > LOGGED_HEADER_MAX_LENGTH ? cleaned.substring(0, LOGGED_HEADER_MAX_LENGTH) + "…"
				: cleaned;
	}

	/** 서명을 확인한 뒤에 파싱한다. GitHub App 웹훅의 본문은 JSON 객체다. */
	private JsonNode parseObject(byte[] body) {
		JsonNode payload;
		try {
			payload = jsonMapper.readTree(body);
		}
		catch (JacksonException ex) {
			throw new BadRequestException("본문이 JSON이 아니에요");
		}
		if (payload == null || !payload.isObject()) {
			throw new BadRequestException("본문이 JSON 객체가 아니에요");
		}
		return payload;
	}

}
