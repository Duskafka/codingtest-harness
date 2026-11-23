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
 * <p>private key는 파일 경로({@code GITHUB_APP_PRIVATE_KEY_PATH}, 운영 기본: 권한 400 읽기 전용 마운트, infra.md 9.6)나
 * 환경 변수({@code GITHUB_APP_PRIVATE_KEY}, PEM 내용)로만 받는다. 둘 중 하나는 있어야 앱이 뜬다. 값은 로그에 찍지 않는다.
 *
 * @param clientId App의 client ID. App JWT의 {@code iss}로 쓴다 (GitHub 권장). 로그인용 client ID와 같은 값이다
 * @param slug App의 URL 이름. 설치 주소 {@code https://github.com/apps/{slug}/installations/new}에 쓴다
 * @param privateKeyPath private key PEM 파일 경로
 * @param privateKey private key PEM 내용 (줄바꿈을 {@code \n} 글자로 넣어도 된다)
 * @param apiBaseUrl REST API 주소. 테스트는 WireMock 주소를 넣는다
 * @param webBaseUrl 웹 주소 (설치 주소를 만든다)
 */
@ConfigurationProperties("studybot.github.app")
@Validated
public record GithubAppProperties(@NotBlank String clientId, @NotBlank String slug, @Nullable String privateKeyPath,
		@Nullable String privateKey, @DefaultValue("https://api.github.com") URI apiBaseUrl,
		@DefaultValue("https://github.com") URI webBaseUrl) {

	/** private key가 로그나 오류 메시지에 찍히지 않게 가린다. */
	@Override
	public String toString() {
		return "GithubAppProperties[clientId=" + clientId + ", slug=" + slug + ", privateKeyPath="
				+ (hasText(privateKeyPath) ? privateKeyPath : "(없음)") + ", privateKey="
				+ (hasText(privateKey) ? "***" : "(없음)") + ", apiBaseUrl=" + apiBaseUrl + ", webBaseUrl=" + webBaseUrl + "]";
	}

	static boolean hasText(@Nullable String value) {
		return value != null && !value.isBlank();
	}

}
