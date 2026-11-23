package com.duskafka.studybot.github.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.duskafka.studybot.github.WebhookSigner;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 서명 검증 (S06 만들 것 2, pseudocode '서명 검증 (Java)', security.md 9.7 '받는 요청'). */
class GithubSignatureVerifierTest {

	private static final String SECRET = "current-secret";

	private static final String PREVIOUS = "previous-secret";

	private static final byte[] BODY = "{\"zen\":\"Keep it logically awesome.\"}".getBytes(StandardCharsets.UTF_8);

	private final GithubSignatureVerifier verifier = new GithubSignatureVerifier(SECRET, null);

	@Test
	@DisplayName("S06: 현재 시크릿으로 원본 바이트에 서명했으면 통과한다")
	void acceptsSignatureWithCurrentSecret() {
		assertThat(verifier.verify(BODY, WebhookSigner.sign(SECRET, BODY))).isTrue();
	}

	@Test
	@DisplayName("T1: 서명이 없으면 거절한다")
	void t1RejectsMissingSignature() {
		assertThat(verifier.verify(BODY, null)).isFalse();
		assertThat(verifier.verify(BODY, "")).isFalse();
	}

	@Test
	@DisplayName("T1: 다른 시크릿으로 서명했거나 본문이 다르면 거절한다")
	void t1RejectsWrongSignature() {
		assertThat(verifier.verify(BODY, WebhookSigner.sign("other-secret", BODY))).isFalse();
		assertThat(verifier.verify("{}".getBytes(StandardCharsets.UTF_8), WebhookSigner.sign(SECRET, BODY))).isFalse();
	}

	@Test
	@DisplayName("S06: sha256= 접두사가 없거나 다른 알고리즘이면 거절한다")
	void rejectsWrongPrefix() {
		String hex = WebhookSigner.sign(SECRET, BODY).substring("sha256=".length());

		assertThat(verifier.verify(BODY, hex)).isFalse();
		assertThat(verifier.verify(BODY, "sha1=" + hex)).isFalse();
		assertThat(verifier.verify(BODY, "SHA256=" + hex)).isFalse();
	}

	@Test
	@DisplayName("S06: 16진수가 아닌 서명은 거절한다")
	void rejectsNonHexSignature() {
		assertThat(verifier.verify(BODY, "sha256=" + "zz".repeat(32))).isFalse();
		assertThat(verifier.verify(BODY, "sha256=" + "g".repeat(64))).isFalse();
	}

	@Test
	@DisplayName("S06: 길이가 틀린 서명(홀수 자리, 짧음, 김)은 거절한다")
	void rejectsSignatureOfWrongLength() {
		String hex = WebhookSigner.sign(SECRET, BODY).substring("sha256=".length());

		assertThat(verifier.verify(BODY, "sha256=" + hex.substring(1))).isFalse();
		assertThat(verifier.verify(BODY, "sha256=" + hex.substring(2))).isFalse();
		assertThat(verifier.verify(BODY, "sha256=" + hex + "00")).isFalse();
		assertThat(verifier.verify(BODY, "sha256=")).isFalse();
	}

	@Test
	@DisplayName("S06: 16진수 대문자도 같은 값으로 읽는다")
	void acceptsUppercaseHex() {
		String hex = WebhookSigner.sign(SECRET, BODY).substring("sha256=".length());

		assertThat(verifier.verify(BODY, "sha256=" + hex.toUpperCase(Locale.ROOT))).isTrue();
	}

	@Test
	@DisplayName("S06: 시크릿 교체 중에는 옛 시크릿과 새 시크릿 서명이 모두 통과한다 (9.6)")
	void acceptsBothSecretsDuringRotation() {
		GithubSignatureVerifier rotating = new GithubSignatureVerifier(SECRET, PREVIOUS);

		assertThat(rotating.verify(BODY, WebhookSigner.sign(SECRET, BODY))).isTrue();
		assertThat(rotating.verify(BODY, WebhookSigner.sign(PREVIOUS, BODY))).isTrue();
		assertThat(rotating.verify(BODY, WebhookSigner.sign("third-secret", BODY))).isFalse();
	}

	@Test
	@DisplayName("S06: 옛 시크릿을 설정하지 않았으면(없음, 빈 값, 공백) 현재 시크릿만 받는다")
	void usesOnlyCurrentSecretWhenPreviousIsNotConfigured() {
		assertThat(verifier.verify(BODY, WebhookSigner.sign(PREVIOUS, BODY))).isFalse();

		GithubSignatureVerifier emptyPrevious = new GithubSignatureVerifier(SECRET, "");
		assertThat(emptyPrevious.verify(BODY, WebhookSigner.sign(SECRET, BODY))).isTrue();

		GithubSignatureVerifier blankPrevious = new GithubSignatureVerifier(SECRET, "  ");
		assertThat(blankPrevious.verify(BODY, WebhookSigner.sign(SECRET, BODY))).isTrue();
		assertThat(blankPrevious.verify(BODY, WebhookSigner.sign("  ", BODY))).isFalse();
	}

	@Test
	@DisplayName("S06: 현재 시크릿이 비어 있으면 검증기를 만들 수 없다")
	void requiresCurrentSecret() {
		assertThatThrownBy(() -> new GithubSignatureVerifier("", null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GithubSignatureVerifier("   ", PREVIOUS)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("S06: 빈 본문도 서명이 맞으면 통과한다")
	void acceptsEmptyBodyWithValidSignature() {
		byte[] empty = new byte[0];

		assertThat(verifier.verify(empty, WebhookSigner.sign(SECRET, empty))).isTrue();
	}

}
