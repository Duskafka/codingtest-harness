package com.duskafka.studybot.github.webhook;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * GitHub 웹훅 설정 ({@code studybot.github.webhook.*}).
 *
 * <p>시크릿은 환경 변수로만 받는다 (S06 만들 것 2, infra.md 9.6): {@code GITHUB_WEBHOOK_SECRET}(필수, 없으면 앱이 뜨지 않음),
 * 교체하는 동안만 {@code GITHUB_WEBHOOK_SECRET_PREVIOUS}. 연결은 main {@code application.yaml}에 있다.
 *
 * @param secret 현재 시크릿 (GitHub App 설정의 Webhook secret)
 * @param previousSecret 교체 중인 옛 시크릿. 교체가 끝나면 비운다
 * @param maxBodySize 본문 한도. 넘으면 413 (5.3 '주의할 것' "예: 5MB")
 */
@ConfigurationProperties("studybot.github.webhook")
@Validated
public record GithubWebhookProperties(@NotBlank String secret, @Nullable String previousSecret,
		@DefaultValue("5MB") DataSize maxBodySize) {

	/** 본문 한도의 상한. 본문을 배열 하나로 읽기 때문에 int 범위 안이어야 하고, 그보다 훨씬 작게 둔다. */
	static final DataSize MAX_BODY_SIZE_LIMIT = DataSize.ofMegabytes(100);

	public GithubWebhookProperties {
		if (maxBodySize.toBytes() <= 0 || maxBodySize.compareTo(MAX_BODY_SIZE_LIMIT) > 0) {
			throw new IllegalArgumentException("본문 한도는 1바이트 이상 " + MAX_BODY_SIZE_LIMIT + " 이하여야 해요: " + maxBodySize);
		}
	}

	/** 시크릿이 로그나 오류 메시지에 찍히지 않게 가린다. */
	@Override
	public String toString() {
		return "GithubWebhookProperties[secret=***, previousSecret=" + (previousSecret == null || previousSecret.isBlank()
				? "(없음)" : "***") + ", maxBodySize=" + maxBodySize + "]";
	}

}
