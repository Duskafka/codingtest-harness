package com.duskafka.studybot.github.webhook;

import org.jspecify.annotations.Nullable;

public final class GithubSignatureVerifier {

	public GithubSignatureVerifier(String secret, @Nullable String previousSecret) {
	}

	public boolean verify(byte[] body, @Nullable String signatureHeader) {
		throw new UnsupportedOperationException("S06 구현 전");
	}

}
