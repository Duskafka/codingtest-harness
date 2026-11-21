package com.duskafka.studybot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * springdoc-openapi로 API 명세를 생성한다 (S01 만들 것 6번, api.md 4.4).
 * 명세 엔드포인트는 기본으로 꺼 두고, {@code springdoc.api-docs.enabled=true}로 켠다 (알려진 충돌 8번, PR 판단 기록).
 */
class OpenApiIntegrationTest {

	@Nested
	@IntegrationTest
	@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
	class WhenEnabled {

		@Autowired
		private MockMvcTester mvc;

		@Test
		@DisplayName("S01: 명세를 켜면 /v3/api-docs가 OpenAPI 3 명세(JSON)를 준다")
		void generatesOpenApiDocument() {
			MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

			assertThat(result).hasStatus(HttpStatus.OK).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
			assertThat(result).bodyJson().extractingPath("$.openapi").asString().startsWith("3.");
			assertThat(result).bodyJson().extractingPath("$.info").isNotNull();
		}

		@Test
		@DisplayName("S01: 테스트 전용 엔드포인트는 다른 테스트의 컨텍스트와 명세에 섞이지 않는다")
		void testOnlyEndpointsAreNotScanned() {
			MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

			assertThat(result).bodyText().doesNotContain("/test/errors");
		}

	}

	@Nested
	@IntegrationTest
	class ByDefault {

		@Autowired
		private MockMvcTester mvc;

		@Test
		@DisplayName("S01: 기본 설정에서는 명세 엔드포인트가 꺼져 있다 (404)")
		void apiDocsAreDisabled() {
			assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(HttpStatus.NOT_FOUND);
		}

	}

}
