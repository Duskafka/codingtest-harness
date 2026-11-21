package com.duskafka.studybot.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 오류 형식: RFC 9457 {@code application/problem+json} (S01 만들 것 5번, docs/spec/api.md 4.4, security.md 9.7).
 * 오류를 일으키는 엔드포인트는 이 테스트에만 등록되는 테스트 전용 컨트롤러다.
 */
@IntegrationTest
@Import(ErrorResponseIntegrationTest.ErrorTestController.class)
class ErrorResponseIntegrationTest {

	private static final String SECRET_MESSAGE = "db password=s3cr3t url=jdbc:postgresql://internal-host:5432/app";

	@Autowired
	private MockMvcTester mvc;

	@Test
	@DisplayName("S01: 없는 경로 요청 → 404 problem+json, 스택 트레이스와 예외 클래스 이름이 없다")
	void unknownPathReturns404Problem() {
		MvcTestResult result = mvc.get().uri("/api/does-not-exist").exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(404);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Not Found");
		assertNoInternals(result);
	}

	@Test
	@DisplayName("S01: 요청 본문 검증 실패 → 400 problem+json, 어느 필드가 왜 틀렸는지 담긴다")
	void invalidBodyReturns400WithFieldErrors() {
		MvcTestResult result = mvc.post()
			.uri("/test/errors/body")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "", "count": 0}
					""")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(400);
		assertThat(result).bodyJson()
			.extractingPath("$.errors[*].field")
			.asArray()
			.containsExactlyInAnyOrder("name", "count");
		assertThat(result).bodyJson()
			.extractingPath("$.errors[*].message")
			.asArray()
			.hasSize(2)
			.allSatisfy(message -> assertThat(message).asString().isNotBlank());
		assertNoInternals(result);
	}

	@Test
	@DisplayName("S01: 요청 파라미터 검증 실패 → 400 problem+json, 파라미터 이름과 이유가 담긴다")
	void invalidParameterReturns400WithFieldErrors() {
		MvcTestResult result = mvc.get().uri("/test/errors/param?page=0").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("page");
		assertThat(result).bodyJson().extractingPath("$.errors[0].message").asString().isNotBlank();
		assertNoInternals(result);
	}

	@Test
	@DisplayName("S01: 형식이 틀린 JSON 본문 → 400 problem+json, 파서 내부 정보가 없다")
	void malformedJsonReturns400() {
		MvcTestResult result = mvc.post()
			.uri("/test/errors/body")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{not json")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyText().doesNotContainIgnoringCase("jackson").doesNotContain("not json");
		assertNoInternals(result);
	}

	@Test
	@DisplayName("S01: 처리하지 못한 예외 → 500 problem+json, 예외 이름·메시지·스택 같은 내부 정보가 없다")
	void unhandledExceptionReturns500WithoutInternals() {
		MvcTestResult result = mvc.get().uri("/test/errors/unhandled").exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(500);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Internal Server Error");
		assertThat(result).bodyText()
			.doesNotContain("IllegalStateException")
			.doesNotContain("s3cr3t")
			.doesNotContain("password")
			.doesNotContain("jdbc")
			.doesNotContain("internal-host");
		assertNoInternals(result);
	}

	@ParameterizedTest(name = "S01: {0} → {1} problem+json, detail은 던진 문장 그대로")
	@CsvSource({ "unauthorized, 401, 로그인이 필요해요.", "forbidden, 403, 이 스터디의 방장만 할 수 있어요.",
			"not-found, 404, 스터디를 찾을 수 없어요.", "conflict, 409, 이미 처리된 요청이에요." })
	@DisplayName("S01: 공통 예외(401·403·404·409)는 해당 상태의 problem+json이 된다")
	void commonExceptionsMapToStatus(String path, int status, String detail) {
		MvcTestResult result = mvc.get().uri("/test/errors/" + path).exchange();

		assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
		assertNoInternals(result);
	}

	private static void assertNoInternals(MvcTestResult result) {
		assertThat(result).bodyText()
			.doesNotContain("Exception")
			.doesNotContain("\tat ")
			.doesNotContain("at com.")
			.doesNotContain("at org.")
			.doesNotContain("trace")
			.doesNotContainIgnoringCase("select ");
	}

	/** 이 테스트에만 등록되는 엔드포인트. 테스트 클래스 안의 클래스라 컴포넌트 스캔에서 빠진다. */
	@RestController
	@RequestMapping("/test/errors")
	static class ErrorTestController {

		record CreateRequest(@NotBlank String name, @Min(1) int count) {
		}

		@PostMapping("/body")
		void body(@Valid @RequestBody CreateRequest request) {
		}

		@GetMapping("/param")
		String param(@RequestParam @Min(1) int page) {
			return "page " + page;
		}

		@GetMapping("/unhandled")
		void unhandled() {
			throw new IllegalStateException(SECRET_MESSAGE);
		}

		@GetMapping("/unauthorized")
		void unauthorized() {
			throw new UnauthorizedException("로그인이 필요해요.");
		}

		@GetMapping("/forbidden")
		void forbidden() {
			throw new ForbiddenException("이 스터디의 방장만 할 수 있어요.");
		}

		@GetMapping("/not-found")
		void notFound() {
			throw new NotFoundException("스터디를 찾을 수 없어요.");
		}

		@GetMapping("/conflict")
		void conflict() {
			throw new ConflictException("이미 처리된 요청이에요.");
		}

	}

}
