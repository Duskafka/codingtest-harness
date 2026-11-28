package com.duskafka.studybot.notification.link;

import java.net.URI;
import java.util.Locale;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 화면 경로를 운영 도메인의 절대 주소로 바꾼다 (S14). 카카오톡 공유·텍스트 복사·디스코드 임베드는 앱 밖에서 열리니 절대 주소가 필요하다 (5.9 '카드
 * 예시' {@code https://(도메인)/studies/12/today}). 화면 경로는 S16이 정하기 전이라 웹 푸시 payload(S13)와 같은 모양으로 둔다.
 *
 * <p>기동할 때 주소를 검사한다: https, 사용자 정보·쿼리·조각 없음, 경로는 비었거나 {@code /}. 틀리면 앱이 뜨지 않는다.
 */
@Component
@EnableConfigurationProperties(PublicLinkProperties.class)
public class PublicLinks {

	private final String origin;

	PublicLinks(PublicLinkProperties properties) {
		this.origin = originOf(properties.publicBaseUrl());
	}

	static String originOf(URI uri) {
		if (uri == null || !uri.isAbsolute() || !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))
				|| uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
				|| uri.getRawFragment() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()
						&& !"/".equals(uri.getRawPath()))) {
			throw new IllegalStateException(
					"studybot.app.public-base-url은 경로 없는 https 주소여야 해요 (예: https://studybot.example)");
		}
		return "https://" + uri.getRawAuthority().toLowerCase(Locale.ROOT);
	}

	/** 오늘 현황 화면. */
	public String today(long studyId) {
		return this.origin + todayPath(studyId);
	}

	/** 특정 날 상세 화면. */
	public String day(long studyId, java.time.LocalDate studyDay) {
		return this.origin + dayPath(studyId, studyDay);
	}

	/** 오늘 현황 화면 경로 (같은 출처, 웹 푸시 payload). */
	public static String todayPath(long studyId) {
		return "/studies/" + studyId + "/today";
	}

	/** 특정 날 상세 화면 경로 (같은 출처, 웹 푸시 payload). */
	public static String dayPath(long studyId, java.time.LocalDate studyDay) {
		return "/studies/" + studyId + "/days/" + studyDay;
	}

	/** 스터디 설정 화면 경로 (같은 출처, 웹 푸시 payload). */
	public static String settingsPath(long studyId) {
		return "/studies/" + studyId + "/settings";
	}

}
