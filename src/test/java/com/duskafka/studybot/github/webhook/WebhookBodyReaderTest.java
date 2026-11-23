package com.duskafka.studybot.github.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 본문 크기 제한 (S06 만들 것 7). 한도를 넘는 본문은 한도 + 1바이트까지만 읽고 거절한다. */
class WebhookBodyReaderTest {

	private static final int MAX = 16;

	@Test
	@DisplayName("S06: 한도 안의 본문은 그대로 읽는다 (Content-Length가 있든 없든)")
	void readsBodyWithinLimit() throws IOException {
		byte[] body = new byte[MAX];

		assertThat(WebhookBodyReader.read(new ByteArrayInputStream(body), MAX, MAX)).isEqualTo(body);
		assertThat(WebhookBodyReader.read(new ByteArrayInputStream(body), -1, MAX)).isEqualTo(body);
		assertThat(WebhookBodyReader.read(new ByteArrayInputStream(new byte[0]), -1, MAX)).isEmpty();
	}

	@Test
	@DisplayName("S06: Content-Length가 한도보다 크면 본문을 읽지 않고 거절한다")
	void rejectsByContentLengthWithoutReading() {
		InputStream mustNotBeRead = new InputStream() {

			@Override
			public int read() {
				throw new AssertionError("본문을 읽으면 안 돼요");
			}

		};

		assertThatThrownBy(() -> WebhookBodyReader.read(mustNotBeRead, MAX + 1, MAX))
			.isInstanceOf(WebhookBodyTooLargeException.class);
	}

	@Test
	@DisplayName("S06: Content-Length가 없거나(chunked) 거짓이어도 한도를 넘으면 거절한다")
	void rejectsOversizedStreamWithoutContentLength() {
		assertThatThrownBy(() -> WebhookBodyReader.read(new ByteArrayInputStream(new byte[MAX + 1]), -1, MAX))
			.isInstanceOf(WebhookBodyTooLargeException.class);
		assertThatThrownBy(() -> WebhookBodyReader.read(new ByteArrayInputStream(new byte[MAX + 1]), 1, MAX))
			.isInstanceOf(WebhookBodyTooLargeException.class);
	}

}
