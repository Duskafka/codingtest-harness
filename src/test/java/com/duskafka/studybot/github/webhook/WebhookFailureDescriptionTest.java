package com.duskafka.studybot.github.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;

/**
 * 수신함 {@code last_error}에 남기는 설명 (사람이 정함, 2026-11-23 S06 세션 "메시지 대신 예외 종류만").
 * 예외 메시지에는 payload 값이나 DB 키 값 같은 개인정보가 들어갈 수 있어 남기지 않는다.
 */
class WebhookFailureDescriptionTest {

	private static final String EMAIL = "tester@example.com";

	@Test
	@DisplayName("S06: 예외 메시지는 남기지 않고 예외 종류만 남긴다")
	void keepsClassNameOnly() {
		String description = WebhookFailureDescription.of(new IllegalStateException(EMAIL + " 때문에 실패"));

		assertThat(description).isEqualTo("java.lang.IllegalStateException");
	}

	@Test
	@DisplayName("S06: 감싼 예외는 바깥에서 안쪽 순서로 종류를 잇는다")
	void chainsCauses() {
		Throwable failure = new RuntimeException("바깥 " + EMAIL, new IllegalArgumentException("안쪽 " + EMAIL));

		assertThat(WebhookFailureDescription.of(failure))
			.isEqualTo("java.lang.RuntimeException ← java.lang.IllegalArgumentException")
			.doesNotContain(EMAIL);
	}

	@Test
	@DisplayName("S06: DB 오류면 SQLState를 남기고, 키 값이 든 메시지는 남기지 않는다")
	void keepsSqlStateButNotDetail() {
		SQLException sql = new SQLException("duplicate key value violates unique constraint; Detail: Key (github_id)=(9001)",
				"23505");
		Throwable failure = new DataIntegrityViolationException("could not execute statement (github_id)=(9001)", sql);

		assertThat(WebhookFailureDescription.of(failure))
			.isEqualTo("org.springframework.dao.DataIntegrityViolationException ← java.sql.SQLException (SQLState 23505)")
			.doesNotContain("9001");
	}

	@Test
	@DisplayName("S06: SQLState가 없으면 붙이지 않는다")
	void omitsMissingSqlState() {
		assertThat(WebhookFailureDescription.of(new SQLException("연결 실패"))).isEqualTo("java.sql.SQLException");
	}

	@Test
	@DisplayName("S06: 처리기가 남기라고 준 문장(WebhookHandlingException)만 메시지를 남긴다. 감싸져 있어도 같다")
	void keepsSafeMessageOfHandlingException() {
		Throwable failure = new TransactionSystemException("바깥 " + EMAIL,
				new WebhookHandlingException("연결하지 않은 레포예요", new IllegalStateException("안쪽 " + EMAIL)));

		assertThat(WebhookFailureDescription.of(failure)).isEqualTo(
				"org.springframework.transaction.TransactionSystemException ← "
						+ "com.duskafka.studybot.github.webhook.WebhookHandlingException: 연결하지 않은 레포예요 ← "
						+ "java.lang.IllegalStateException")
			.doesNotContain(EMAIL);
	}

	@Test
	@DisplayName("S06: 원인 사슬은 5단계까지만 남기고 나머지는 줄임표로 표시한다")
	void limitsDepth() {
		Throwable failure = new IllegalStateException("맨 안쪽");
		for (int i = 0; i < 9; i++) {
			failure = new RuntimeException("감쌈 " + i, failure);
		}

		String description = WebhookFailureDescription.of(failure);

		assertThat(List.of(description.split(" ← "))).hasSize(6).last().isEqualTo("…");
		assertThat(description).doesNotContain("IllegalStateException");
	}

	@Test
	@DisplayName("S06: 원인이 서로를 가리켜도(순환) 끝나고, 같은 예외를 두 번 적지 않는다")
	void stopsOnCycle() {
		IllegalStateException first = new IllegalStateException("a");
		IllegalArgumentException second = new IllegalArgumentException("b");
		first.initCause(second);
		second.initCause(first);

		assertThat(WebhookFailureDescription.of(first))
			.isEqualTo("java.lang.IllegalStateException ← java.lang.IllegalArgumentException");
	}

}
