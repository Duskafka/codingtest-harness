package com.duskafka.studybot.github.api;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * GitHub App 설정 ({@code studybot.github.app.*}, S07 만들 것 4). 연결은 main {@code application.yaml}에 있다.
 *
 * <p>private key는 서버 파일 경로({@code GITHUB_APP_PRIVATE_KEY_PATH})로만 받는다. 운영은 권한 400 읽기 전용 마운트다
 * (infra.md 9.6, 사람이 정함 2026-11-24 "파일만"). 키 내용을 환경 변수로 받지 않는다. 경로가 없으면 앱이 뜨지 않는다.
 *
 * @param clientId App의 client ID. App JWT의 {@code iss}로 쓴다 (GitHub 권장). 로그인용 client ID와 같은 값이다
 * @param slug App의 URL 이름. 설치 주소 {@code https://github.com/apps/{slug}/installations/new}에 쓴다
 * @param privateKeyPath private key PEM 파일 경로 (GitHub가 주는 PKCS#1 그대로, PKCS#8도 받음)
 * @param apiBaseUrl REST API 주소. 테스트는 WireMock 주소를 넣는다
 * @param webBaseUrl 웹 주소 (설치 주소를 만든다)
 */
@ConfigurationProperties("studybot.github.app")
@Validated
public record GithubAppProperties(@NotBlank String clientId, @NotBlank String slug, @Nullable String privateKeyPath,
		@DefaultValue("https://api.github.com") URI apiBaseUrl, @DefaultValue("https://github.com") URI webBaseUrl) {

	static boolean hasText(@Nullable String value) {
		return value != null && !value.isBlank();
	}

}
