package com.duskafka.studybot.github.webhook;

import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.common.error.UnauthorizedException;
import com.duskafka.studybot.github.webhook.GithubWebhookReceiver.Receipt;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
 * <tr><td>본문이 한도보다 큼</td><td>413</td></tr>
 * <tr><td>서명이 없거나 틀림</td><td>401</td></tr>
 * <tr><td>이벤트·전달 ID 헤더가 없음, 본문이 JSON 객체가 아님</td><td>400</td></tr>
 * <tr><td>관심 없는 이벤트</td><td>204 (남기지 않음)</td></tr>
 * <tr><td>수신함에 저장하지 못함</td><td>503</td></tr>
 * <tr><td>이미 받은 전달, ping</td><td>200</td></tr>
 * <tr><td>저장 성공 (처리 성공·실패와 상관없이)</td><td>202</td></tr>
 * </table>
 */
@RestController
class GithubWebhookController {

	static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

	static final String EVENT_HEADER = "X-GitHub-Event";

	static final String DELIVERY_HEADER = "X-GitHub-Delivery";

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
		byte[] body = WebhookBodyReader.read(request.getInputStream(), request.getContentLengthLong(), maxBodyBytes);
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
