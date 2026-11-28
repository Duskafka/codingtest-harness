package com.duskafka.studybot.notification.discord;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 등록할 디스코드 웹훅 주소를 검사한다 (T27, security.md 9.7 '보내는 요청 (SSRF 방지)': 공식 주소로 시작하는 것만 허용).
 *
 * <ul>
 * <li>https만, 사용자 정보({@code user@}) 없이, 기본 포트(443)만, 쿼리·조각 없이, {@value #MAX_LENGTH}자까지.</li>
 * <li>호스트는 디스코드 공식 도메인과 정확히 같아야 한다: {@code discord.com}, 옛 도메인 {@code discordapp.com}, 미리보기 앱의
 * {@code ptb.discord.com}·{@code canary.discord.com}. 소문자로 비교한다. IP 주소·내부 이름·다른 도메인은 여기서 모두 걸린다.</li>
 * <li>경로는 {@code /api/webhooks/{숫자 ID}/{토큰}}이다. API 버전이 붙은 {@code /api/v10/webhooks/…}도 받는다. 그 밖의 경로(슬랙 호환 경로,
 * {@code ..} 등)는 받지 않는다.</li>
 * </ul>
 *
 * <p>통과한 주소는 웹훅 ID와 토큰만 남긴다. 보낼 때는 설정한 디스코드 API 주소에 이 둘을 붙인다. 그래서 사용자가 준 호스트로는 요청이 가지 않는다.
 * 앞뒤 공백은 지운다(복사해 붙일 때 생김).
 */
public final class DiscordWebhookPolicy {

	/** 받을 주소의 최대 길이. 실제 웹훅 주소는 약 120자다(추측: 토큰 68자 기준). */
	public static final int MAX_LENGTH = 512;

	static final Set<String> OFFICIAL_HOSTS = Set.of("discord.com", "discordapp.com", "ptb.discord.com",
			"canary.discord.com");

	private static final Pattern PATH = Pattern
		.compile("/api(?:/v[0-9]{1,2})?/webhooks/([0-9]{1,20})/([A-Za-z0-9_-]{1,100})");

	private DiscordWebhookPolicy() {
	}

	/** 공식 웹훅 주소면 웹훅 ID·토큰, 아니면 빈 값. */
	public static Optional<DiscordWebhook> parse(String url) {
		if (url == null) {
			return Optional.empty();
		}
		String value = url.strip();
		if (value.isEmpty() || value.length() > MAX_LENGTH) {
			return Optional.empty();
		}
		URI uri;
		try {
			uri = new URI(value);
		}
		catch (java.net.URISyntaxException ex) {
			return Optional.empty();
		}
		if (!uri.isAbsolute() || uri.isOpaque() || !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))
				|| uri.getRawUserInfo() != null || uri.getHost() == null
				|| !OFFICIAL_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
				|| (uri.getPort() != -1 && uri.getPort() != 443) || uri.getRawQuery() != null
				|| uri.getRawFragment() != null || uri.getRawPath() == null) {
			return Optional.empty();
		}
		Matcher matcher = PATH.matcher(uri.getRawPath());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		return Optional.of(new DiscordWebhook(matcher.group(1), matcher.group(2)));
	}

}
