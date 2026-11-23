package com.duskafka.studybot.github.webhook;

import java.io.IOException;
import java.io.InputStream;

final class WebhookBodyReader {

	private WebhookBodyReader() {
	}

	static byte[] read(InputStream in, long contentLength, long maxBytes) throws IOException {
		throw new UnsupportedOperationException("S06 구현 전");
	}

}
