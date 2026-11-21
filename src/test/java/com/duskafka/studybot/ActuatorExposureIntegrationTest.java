package com.duskafka.studybot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Actuator는 health만 노출한다 (S01 만들 것 7번, security.md 9.7 '운영 엔드포인트와 오류').
 * env·heapdump·configprops가 노출되면 비밀번호·키가 그대로 유출된다 [S57].
 */
@IntegrationTest
class ActuatorExposureIntegrationTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	@DisplayName("S01: /actuator/health는 200이고 상태만 보여 준다 (세부 정보 없음)")
	void healthIsExposed() {
		MvcTestResult result = mvc.get().uri("/actuator/health").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UP");
		assertThat(result).bodyText().doesNotContain("components").doesNotContain("details");
	}

	@Test
	@DisplayName("S01: Actuator 링크 목록(/actuator)도 노출하지 않는다 (api.md 4.4: 외부 노출은 health만)")
	void discoveryPageIsNotExposed() {
		assertThat(mvc.get().uri("/actuator")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@ParameterizedTest(name = "S01: /actuator/{0}는 노출되지 않는다 (404)")
	@ValueSource(strings = { "env", "heapdump", "configprops", "beans", "mappings", "loggers", "info", "metrics",
			"threaddump", "shutdown" })
	@DisplayName("S01: health 말고는 Actuator 엔드포인트가 노출되지 않는다")
	void otherEndpointsAreNotExposed(String endpoint) {
		assertThat(mvc.get().uri("/actuator/" + endpoint)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.post().uri("/actuator/" + endpoint)).hasStatus4xxClientError();
	}

}
