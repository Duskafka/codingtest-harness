package com.duskafka.studybot.github.webhook;

import java.io.IOException;
import java.io.InputStream;

/**
 * 웹훅 본문을 한도까지만 읽는다 (S06 만들 것 7, 5.3 '주의할 것'). 서명은 이 바이트로 확인한다 [S36].
 *
 * <p>Content-Length가 한도보다 크면 읽지 않고 거절한다. Content-Length가 없거나(chunked) 실제보다 작게 적혀 있어도
 * 한도 + 1바이트까지만 읽어서, 넘으면 거절한다. 그래서 메모리에 한도보다 많이 올라가지 않는다.
 */
final class WebhookBodyReader {

	private WebhookBodyReader() {
	}

	/**
	 * @param contentLength 요청의 Content-Length. 모르면 음수
	 * @param maxBytes 한도 (바이트). {@code Integer.MAX_VALUE}보다 작아야 한다
	 * @throws WebhookBodyTooLargeException 한도를 넘음
	 */
	static byte[] read(InputStream in, long contentLength, long maxBytes) throws IOException {
		if (contentLength > maxBytes) {
			throw new WebhookBodyTooLargeException();
		}
		byte[] body = in.readNBytes(Math.toIntExact(maxBytes + 1));
		if (body.length > maxBytes) {
			throw new WebhookBodyTooLargeException();
		}
		return body;
	}

}
