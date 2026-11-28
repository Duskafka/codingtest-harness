package com.duskafka.studybot.notification.link;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 화면 주소 설정 (S14 공유 카드·디스코드 게시의 링크).
 *
 * @param publicBaseUrl 운영 도메인 (예: {@code https://studybot.example}). https이고 경로·쿼리가 없어야 한다. 환경 변수로만 받고 기본값이
 * 없다 ({@code STUDYBOT_PUBLIC_BASE_URL}). 웹 푸시 payload는 같은 출처의 상대 경로를 쓰니 이 값이 필요 없다
 */
@ConfigurationProperties("studybot.app")
public record PublicLinkProperties(URI publicBaseUrl) {

}
