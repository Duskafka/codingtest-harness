package com.duskafka.studybot.github;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;

/**
 * 테스트 안에서 만든 GitHub App 키 (external-systems.md '개발 중에 외부 시스템 다루기': 테스트에는 테스트 안에서 만든 키를 써요).
 * 실제 App의 키가 아니고, JVM이 뜰 때마다 새로 만든다. 레포에 키 파일을 두지 않는다.
 *
 * <p>GitHub가 내려주는 키는 PKCS#1({@code BEGIN RSA PRIVATE KEY}) PEM이라
 * (<a href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/managing-private-keys-for-github-apps">GitHub 문서</a>),
 * 앱 설정에는 그 형식을 임시 파일로 써서 경로({@link #pkcs1PemFile()})를 넣는다. JDK는 PKCS#8로만 내보내서 PKCS#1 부분을
 * 꺼내 쓴다.
 */
public final class GithubAppTestKeys {

	private static final KeyPair KEY_PAIR = generate();

	private GithubAppTestKeys() {
	}

	public static KeyPair keyPair() {
		return KEY_PAIR;
	}

	public static RSAPublicKey publicKey() {
		return (RSAPublicKey) KEY_PAIR.getPublic();
	}

	/** PKCS#8 PEM ({@code BEGIN PRIVATE KEY}). */
	public static String pkcs8Pem() {
		return pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded());
	}

	/** PKCS#1 PEM ({@code BEGIN RSA PRIVATE KEY}). GitHub가 내려주는 형식이다. */
	public static String pkcs1Pem() {
		return pem("RSA PRIVATE KEY", pkcs1Der(KEY_PAIR.getPrivate().getEncoded()));
	}

	/**
	 * {@link #pkcs1Pem()}을 임시 파일에 한 번 써 두고 그 경로를 돌려준다. 앱은 private key를 파일 경로로만 받는다
	 * (infra.md 9.6, {@code GITHUB_APP_PRIVATE_KEY_PATH}). 파일은 JVM이 끝날 때 지운다.
	 */
	public static String pkcs1PemFile() {
		return KeyFile.PATH;
	}

	/** PEM 본문(base64)만. 로그에 키가 남지 않았는지 찾을 때 쓴다. */
	public static String pkcs1Base64() {
		return Base64.getEncoder().encodeToString(pkcs1Der(KEY_PAIR.getPrivate().getEncoded()));
	}

	/** 처음 쓸 때 한 번만 파일을 만든다 (클래스 초기화는 스레드 안전하다). */
	private static final class KeyFile {

		private static final String PATH = write();

		private static String write() {
			try {
				Path file = Files.createTempFile("studybot-test-github-app-", ".pem");
				file.toFile().deleteOnExit();
				Files.writeString(file, pkcs1Pem(), StandardCharsets.US_ASCII);
				return file.toAbsolutePath().toString();
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

	static String pem(String type, byte[] der) {
		String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
		return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
	}

	/**
	 * PKCS#8 PrivateKeyInfo에서 PKCS#1 RSAPrivateKey를 꺼낸다.
	 * {@code SEQUENCE { INTEGER version, SEQUENCE algorithm, OCTET STRING privateKey }}의 세 번째 값이다.
	 */
	static byte[] pkcs1Der(byte[] pkcs8) {
		int[] outer = header(pkcs8, 0, 0x30);
		int[] version = header(pkcs8, outer[0], 0x02);
		int[] algorithm = header(pkcs8, version[0] + version[1], 0x30);
		int[] key = header(pkcs8, algorithm[0] + algorithm[1], 0x04);
		return Arrays.copyOfRange(pkcs8, key[0], key[0] + key[1]);
	}

	/** DER 값 하나의 머리를 읽는다. 돌려주는 값: {내용 시작 위치, 내용 길이}. */
	private static int[] header(byte[] der, int offset, int expectedTag) {
		if ((der[offset] & 0xff) != expectedTag) {
			throw new IllegalArgumentException("DER 태그가 다르다: " + offset);
		}
		int first = der[offset + 1] & 0xff;
		if (first < 0x80) {
			return new int[] { offset + 2, first };
		}
		int lengthBytes = first & 0x7f;
		int length = 0;
		for (int i = 0; i < lengthBytes; i++) {
			length = (length << 8) | (der[offset + 2 + i] & 0xff);
		}
		return new int[] { offset + 2 + lengthBytes, length };
	}

	private static KeyPair generate() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
