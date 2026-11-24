package com.duskafka.studybot.github;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.github.WebhookTestConfiguration.DatabaseOutage;
import com.duskafka.studybot.github.WebhookTestConfiguration.RecordingPushHandler;
import com.duskafka.studybot.github.WebhookTestConfiguration.RecordingStudyDayAssignment;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * S06 웹훅 통합 테스트의 공통 도우미. GitHub가 보내는 헤더(이벤트, 전달 ID, 서명)를 붙여 {@code POST /webhooks/github}를
 * 보내고, 수신함 행을 읽는다. 본문은 {@code src/test/resources/github-payloads/}의 샘플이다.
 *
 * <p>테스트끼리 DB를 공유하므로 전달 ID는 테스트마다 새로 만들고({@link #newDeliveryId()}), 수신함은 전달 ID로 찾는다.
 */
@IntegrationTest
@Import(WebhookTestConfiguration.class)
abstract class WebhookTestSupport {

	static final String WEBHOOK_URL = "/webhooks/github";

	static final String EVENT_HEADER = "X-GitHub-Event";

	static final String DELIVERY_HEADER = "X-GitHub-Delivery";

	static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

	static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected JdbcClient jdbc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected RecordingPushHandler pushHandler;

	@Autowired
	protected RecordingStudyDayAssignment studyDayAssignment;

	@Autowired
	protected DatabaseOutage databaseOutage;

	@BeforeEach
	void resetDoubles() {
		databaseOutage.stop();
		pushHandler.reset();
		studyDayAssignment.reset();
	}

	@AfterEach
	void endOutage() {
		databaseOutage.stop();
	}

	static String newDeliveryId() {
		return UUID.randomUUID().toString();
	}

	static byte[] payload(String fileName) {
		String path = "/github-payloads/" + fileName;
		try (InputStream in = Objects.requireNonNull(WebhookTestSupport.class.getResourceAsStream(path), path)) {
			return in.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** GitHub처럼 현재 시크릿으로 서명해서 보낸다. */
	MvcTestResult sendSigned(String event, String deliveryId, byte[] body) {
		return send(event, deliveryId, body, WebhookSigner.sign(body));
	}

	/** 헤더를 직접 정해서 보낸다. 값이 {@code null}인 헤더는 붙이지 않는다. */
	MvcTestResult send(@Nullable String event, @Nullable String deliveryId, byte[] body, @Nullable String signature) {
		MockMvcRequestBuilder request = mvc.post()
			.uri(WEBHOOK_URL)
			.contentType(MediaType.APPLICATION_JSON)
			.header("User-Agent", "GitHub-Hookshot/test")
			.content(body);
		if (event != null) {
			request = request.header(EVENT_HEADER, event);
		}
		if (deliveryId != null) {
			request = request.header(DELIVERY_HEADER, deliveryId);
		}
		if (signature != null) {
			request = request.header(SIGNATURE_HEADER, signature);
		}
		return request.exchange();
	}

	List<InboxRow> inboxRows(String deliveryId) {
		return jdbc.sql("""
				select id, source, delivery_id, event_type, payload::text as payload, received_at, status, attempts,
					next_attempt_at, last_error, processed_at
				from studybot.webhook_inbox where delivery_id = :deliveryId
				""")
			.param("deliveryId", deliveryId)
			.query((rs, rowNum) -> new InboxRow(rs.getLong("id"), rs.getString("source"), rs.getString("delivery_id"),
					rs.getString("event_type"), rs.getString("payload"), instant(rs.getObject("received_at", OffsetDateTime.class)),
					rs.getString("status"), rs.getInt("attempts"),
					instant(rs.getObject("next_attempt_at", OffsetDateTime.class)), rs.getString("last_error"),
					instant(rs.getObject("processed_at", OffsetDateTime.class))))
			.list();
	}

	InboxRow onlyInboxRow(String deliveryId) {
		List<InboxRow> rows = inboxRows(deliveryId);
		if (rows.size() != 1) {
			throw new AssertionError("수신함 행이 1개가 아니에요: " + rows.size());
		}
		return rows.getFirst();
	}

	static JsonNode json(String text) {
		return JSON.readTree(text);
	}

	static JsonNode json(byte[] bytes) {
		return JSON.readTree(bytes);
	}

	private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
		return value != null ? value.toInstant() : null;
	}

	record InboxRow(long id, String source, String deliveryId, String eventType, @Nullable String payload,
			Instant receivedAt, String status, int attempts, @Nullable Instant nextAttemptAt, @Nullable String lastError,
			@Nullable Instant processedAt) {
	}

}
