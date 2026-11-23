package com.duskafka.studybot.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * S06 수용 기준: DB에 저장할 수 없으면 503 (5.3 응답 코드 규칙). GitHub는 실패한 전달을 스스로 다시 보내지 않지만
 * [S34], 실패로 남아야 나중에 재전송할 수 있다 [S35]. DB 장애는 {@link WebhookTestConfiguration.DatabaseOutage}로 흉내 낸다.
 */
class GithubWebhookDatabaseFailureIntegrationTest extends WebhookTestSupport {

	private static final byte[] PUSH = payload("push.json");

	@Test
	@DisplayName("S06: DB에 저장할 수 없으면 503이고 처리기는 불리지 않는다")
	void databaseOutageWhileStoringIsAnswered503() {
		String delivery = newDeliveryId();
		databaseOutage.start();

		MvcTestResult result = sendSigned("push", delivery, PUSH);

		databaseOutage.stop();
		assertThat(result).hasStatus(503);
		assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(inboxRows(delivery)).isEmpty();
		assertThat(pushHandler.calls()).isEmpty();
	}

	@Test
	@DisplayName("S06: 저장한 뒤 처리 실패를 기록하는 중에 DB가 끊겨도 202다 (받기는 성공, 행은 RECEIVED로 남음)")
	void databaseOutageWhileRecordingFailureStillAnswers202() {
		String delivery = newDeliveryId();
		pushHandler.willDo(event -> {
			databaseOutage.start();
			throw new IllegalStateException("처리 중 DB 장애");
		});

		MvcTestResult result = sendSigned("push", delivery, PUSH);

		databaseOutage.stop();
		assertThat(result).hasStatus(202);
		InboxRow row = onlyInboxRow(delivery);
		assertThat(row.status()).isEqualTo("RECEIVED");
		assertThat(row.attempts()).isZero();
	}

}
