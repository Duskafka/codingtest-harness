package com.duskafka.studybot;

import com.duskafka.studybot.github.GithubApiStub;
import com.duskafka.studybot.github.GithubAppTestKeys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * GitHub App 설정을 테스트 값으로 채운다 (S07). 모든 통합 테스트 컨텍스트가 같은 값을 받아 컨텍스트 공유 구조는 그대로다.
 *
 * <ul>
 * <li>private key: 테스트 안에서 만든 키({@link GithubAppTestKeys}). GitHub가 주는 PKCS#1 형식으로 넣는다.</li>
 * <li>API 주소: {@link GithubApiStub}(WireMock). 테스트는 실제 GitHub에 접속하지 않는다.</li>
 * </ul>
 *
 * client ID·slug 같은 고정 가짜 값은 {@code src/test/resources/config/application.yaml}에 있다.
 */
@TestConfiguration(proxyBeanMethods = false)
class GithubAppTestConfiguration {

	@Bean
	DynamicPropertyRegistrar githubAppProperties() {
		return registry -> {
			registry.add("studybot.github.app.private-key", GithubAppTestKeys::pkcs1Pem);
			registry.add("studybot.github.app.api-base-url", GithubApiStub::baseUrl);
		};
	}

}
