package com.duskafka.studybot.notification.webpush;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.VAPIDKeyPairs;
import com.zerodeplibs.webpush.key.PrivateKeySources;
import com.zerodeplibs.webpush.key.PublicKeySources;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * VAPID 키 쌍 (RFC 8292, S13 '주의할 것': 한 번 만들면 바꾸지 않음, 개인키는 환경 변수로만). 기동할 때 한 번 읽고 검사한다. 키가 없거나,
 * 형식이 틀리거나, 공개키와 개인키가 짝이 아니면 앱이 뜨지 않는다. 오류 메시지에는 키 값을 싣지 않는다.
 *
 * <p>P-256 공개키 검사({@link #parsePoint})는 구독의 {@code p256dh} 검사에도 쓴다.
 */
public final class VapidKeys {

	private static final ECParameterSpec P256 = p256();

	private final String publicKeyBase64Url;

	private final String subject;

	private final VAPIDKeyPair keyPair;

	private VapidKeys(String publicKeyBase64Url, String subject, VAPIDKeyPair keyPair) {
		this.publicKeyBase64Url = publicKeyBase64Url;
		this.subject = subject;
		this.keyPair = keyPair;
	}

	static VapidKeys from(WebPushProperties.Vapid vapid) {
		if (vapid == null || isMissing(vapid.publicKey()) || isMissing(vapid.privateKey())) {
			throw new IllegalStateException(
					"웹 푸시 VAPID 키가 없어요. WEBPUSH_VAPID_PUBLIC_KEY와 WEBPUSH_VAPID_PRIVATE_KEY를 넣어 주세요 (infra.md 9.6)");
		}
		if (isMissing(vapid.subject())
				|| !(vapid.subject().startsWith("mailto:") || vapid.subject().startsWith("https://"))) {
			throw new IllegalStateException("WEBPUSH_VAPID_SUBJECT는 mailto: 또는 https:// 로 시작해야 해요 (RFC 8292)");
		}
		ECPublicKey publicKey = parsePoint(decode(vapid.publicKey().strip()))
			.orElseThrow(() -> new IllegalStateException("WEBPUSH_VAPID_PUBLIC_KEY가 P-256 공개키(65바이트, base64url)가 아니에요"));
		ECPrivateKey privateKey = parsePrivate(decode(vapid.privateKey().strip()));
		requireSameKeyPair(privateKey, publicKey);
		VAPIDKeyPair keyPair = VAPIDKeyPairs.of(PrivateKeySources.ofECPrivateKey(privateKey),
				PublicKeySources.ofECPublicKey(publicKey));
		return new VapidKeys(vapid.publicKey().strip(), vapid.subject(), keyPair);
	}

	/** 브라우저 {@code applicationServerKey}로 줄 공개키 (base64url). */
	public String publicKeyBase64Url() {
		return this.publicKeyBase64Url;
	}

	String subject() {
		return this.subject;
	}

	VAPIDKeyPair keyPair() {
		return this.keyPair;
	}

	/**
	 * 압축하지 않은 P-256 점(0x04 || X || Y, 65바이트)을 공개키로 읽는다. 길이·머리 바이트가 틀리거나 곡선 위의 점이 아니면 빈 값이다.
	 * 곡선 위인지는 직접 확인한다 ({@code y^2 = x^3 - 3x + b mod p}). 키 팩토리가 확인한다고 기대지 않는다.
	 */
	public static Optional<ECPublicKey> parsePoint(byte[] point) {
		if (point.length != 65 || point[0] != 0x04) {
			return Optional.empty();
		}
		BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 33));
		BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 33, 65));
		BigInteger p = ((java.security.spec.ECFieldFp) P256.getCurve().getField()).getP();
		if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
			return Optional.empty();
		}
		BigInteger left = y.modPow(BigInteger.TWO, p);
		BigInteger right = x.pow(3).add(P256.getCurve().getA().multiply(x)).add(P256.getCurve().getB()).mod(p);
		if (!left.equals(right)) {
			return Optional.empty();
		}
		try {
			return Optional.of((ECPublicKey) KeyFactory.getInstance("EC")
				.generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256)));
		}
		catch (GeneralSecurityException ex) {
			return Optional.empty();
		}
	}

	/** base64url(패딩 있어도 됨)을 바이트로. 틀리면 빈 배열이다. */
	public static byte[] decode(String base64Url) {
		try {
			return Base64.getUrlDecoder().decode(base64Url);
		}
		catch (IllegalArgumentException ex) {
			return new byte[0];
		}
	}

	private static ECPrivateKey parsePrivate(byte[] scalar) {
		BigInteger s = new BigInteger(1, scalar);
		if (scalar.length != 32 || s.signum() == 0 || s.compareTo(P256.getOrder()) >= 0) {
			throw new IllegalStateException("WEBPUSH_VAPID_PRIVATE_KEY가 P-256 개인키(32바이트, base64url)가 아니에요");
		}
		try {
			return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(s, P256));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("WEBPUSH_VAPID_PRIVATE_KEY를 읽지 못했어요");
		}
	}

	/** 개인키로 서명한 것을 공개키로 검증해 짝인지 본다. 짝이 아니면 브라우저가 모든 푸시를 거절한다. */
	private static void requireSameKeyPair(ECPrivateKey privateKey, ECPublicKey publicKey) {
		byte[] probe = "studybot-vapid-key-check".getBytes(StandardCharsets.US_ASCII);
		try {
			Signature signer = Signature.getInstance("SHA256withECDSA");
			signer.initSign(privateKey);
			signer.update(probe);
			byte[] signature = signer.sign();
			Signature verifier = Signature.getInstance("SHA256withECDSA");
			verifier.initVerify(publicKey);
			verifier.update(probe);
			if (!verifier.verify(signature)) {
				throw new IllegalStateException("WEBPUSH_VAPID_PUBLIC_KEY와 WEBPUSH_VAPID_PRIVATE_KEY가 짝이 아니에요");
			}
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("VAPID 키를 확인하지 못했어요");
		}
	}

	private static ECParameterSpec p256() {
		try {
			AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
			parameters.init(new ECGenParameterSpec("secp256r1"));
			return parameters.getParameterSpec(ECParameterSpec.class);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("P-256 곡선을 쓸 수 없어요", ex);
		}
	}

	/** 비었거나, 환경 변수가 없어 자리 표시자({@code ${WEBPUSH_...}})가 그대로 남은 값. */
	private static boolean isMissing(String value) {
		return value == null || value.isBlank() || value.startsWith("${");
	}

	/** 개인키를 숨긴다. */
	@Override
	public String toString() {
		return "VapidKeys[publicKey=" + this.publicKeyBase64Url + "]";
	}

}
