package com.duskafka.studybot.notification.webpush;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹 푸시 설정 (S13 만들 것 5·6, infra.md 9.6 'VAPID 개인키', security.md 9.7).
 *
 * @param vapid VAPID 키와 연락처 (RFC 8292). 키는 환경 변수로만 받고 기본값이 없다. 없으면 앱이 뜨지 않는다
 * @param allowedHosts 받을 구독 주소의 호스트 (T28). {@code *.example.com}은 그 아래 호스트만(자신은 아님), 그 밖은 정확히 같은 호스트
 */
@ConfigurationProperties("studybot.webpush")
public record WebPushProperties(Vapid vapid, List<String> allowedHosts) {

	public WebPushProperties {
		allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
	}

	/**
	 * VAPID 설정.
	 *
	 * @param publicKey 압축하지 않은 P-256 공개키(65바이트)의 base64url. 브라우저 {@code applicationServerKey} 형식
	 * @param privateKey P-256 개인키 스칼라(32바이트)의 base64url. 비밀값이다. 로그·오류 메시지에 싣지 않는다
	 * @param subject 연락처 ({@code mailto:} 또는 {@code https:}). JWT {@code sub}
	 */
	public record Vapid(String publicKey, String privateKey, String subject) {

		/** 개인키를 숨긴다. 설정 값이 로그·오류에 찍혀도 키가 남지 않게 한다. */
		@Override
		public String toString() {
			return "Vapid[publicKey=" + this.publicKey + ", privateKey=(숨김), subject=" + this.subject + "]";
		}

	}

}
