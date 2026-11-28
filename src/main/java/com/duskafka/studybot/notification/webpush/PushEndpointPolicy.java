package com.duskafka.studybot.notification.webpush;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * 구독 주소 검사 (security.md 9.7 '보내는 요청 (SSRF 방지)', T28). 서버는 구독 주소로 요청을 보내므로, 알려진 푸시 서비스가 아닌 주소(내부
 * 주소, 클라우드 메타데이터 주소 등)는 등록할 때 거절한다.
 *
 * <ul>
 * <li>{@code https}만, 사용자 정보({@code user@}) 없이, 기본 포트(443)만, 2048자까지.</li>
 * <li>호스트가 허용 목록에 있어야 한다. {@code *.push.apple.com}은 그 아래 호스트만 맞고({@code web.push.apple.com}), 그 밖은 정확히 같아야
 * 한다. 비교는 소문자로, 경로 조각이 아니라 점으로 나눈 이름 단위로 한다({@code evilnotify.windows.com}은 {@code *.notify.windows.com}과
 * 맞지 않음). IP 주소는 목록에 없으니 늘 거절된다.</li>
 * </ul>
 *
 * <p>허용 목록 기본값은 {@code application.yaml}에 있다 (FCM, Mozilla, Apple, Windows. 근거는 S13 PR 판단 기록).
 */
public final class PushEndpointPolicy {

	/** 구독 주소 길이 한도 (V11 CHECK와 같음). */
	public static final int MAX_LENGTH = 2048;

	private final List<String> allowedHosts;

	public PushEndpointPolicy(List<String> allowedHosts) {
		this.allowedHosts = allowedHosts.stream().map(host -> host.strip().toLowerCase(Locale.ROOT)).toList();
		if (this.allowedHosts.isEmpty()) {
			throw new IllegalStateException("허용할 푸시 서비스 호스트(studybot.webpush.allowed-hosts)가 없어요");
		}
	}

	/** 받을 수 있는 구독 주소인지. */
	public boolean allows(String endpoint) {
		if (endpoint == null || endpoint.isEmpty() || endpoint.length() > MAX_LENGTH) {
			return false;
		}
		URI uri;
		try {
			uri = new URI(endpoint);
		}
		catch (URISyntaxException ex) {
			return false;
		}
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getHost() == null) {
			return false;
		}
		if (uri.getPort() != -1 && uri.getPort() != 443) {
			return false;
		}
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		return this.allowedHosts.stream().anyMatch(allowed -> matches(allowed, host));
	}

	private static boolean matches(String allowed, String host) {
		if (allowed.startsWith("*.")) {
			String suffix = allowed.substring(1);
			return host.endsWith(suffix) && host.length() > suffix.length();
		}
		return host.equals(allowed);
	}

}
