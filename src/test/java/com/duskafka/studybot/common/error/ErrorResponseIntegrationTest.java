package com.duskafka.studybot.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
import org.springframework.web.bind.annotation.ModelAttribute;
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

	@Test
	@DisplayName("S01: 쿼리 객체(@ModelAttribute)의 타입이 틀림 → 400, 필드는 담고 Java 타입 이름·변환 예외 메시지는 없다")
	void typeMismatchInQueryObjectReturns400WithoutInternals() {
		MvcTestResult result = mvc.get().uri("/test/errors/query?page=abc").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("page");
		assertThat(result).bodyJson().extractingPath("$.errors[0].message").asString().isNotBlank();
		assertThat(result).bodyText().doesNotContain("java.").doesNotContain("For input string");
		assertNoInternals(result);
	}

	@Test
	@DisplayName("S01: 요청 파라미터의 타입이 틀림 → 400, Java 타입 이름이 없다")
	void typeMismatchInParameterReturns400WithoutInternals() {
		MvcTestResult result = mvc.get().uri("/test/errors/param?page=abc").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyText().doesNotContain("java.").doesNotContain("For input string");
		assertNoInternals(result);
	}

	@Test
	@DisplayName("S01: 컨트롤러 반환값 검증 실패 → 500 problem+json, 요청 오류 목록(errors)과 서버 쪽 검증 내용이 없다")
	void returnValueValidationFailureReturns500WithoutErrors() {
		MvcTestResult result = mvc.get().uri("/test/errors/return-value").exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Internal Server Error");
		assertThat(result).bodyJson().doesNotHavePath("$.errors");
		assertThat(result).bodyText().doesNotContain("arg-").doesNotContain("must not be null");
		assertNoInternals(result);
	}

	@Test
	@DisplayName("S01: 메서드 검증 경로의 객체 전체 오류 → field는 null, 파라미터 오류는 파라미터 이름")
	void objectLevelErrorInMethodValidationHasNullField() {
		MvcTestResult result = mvc.post()
			.uri("/test/errors/object-and-param?page=0")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"min": 5, "max": 1}
					""")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson()
			.extractingPath("$.errors[*].field")
			.asArray()
			.containsExactlyInAnyOrder(null, "page");
		assertThat(result).bodyJson()
			.extractingPath("$.errors[*].message")
			.asArray()
			.contains(ErrorTestController.RANGE_MESSAGE);
		assertNoInternals(result);
	}

	@Test
	@DisplayName("S01: 요청 파라미터 이름을 따로 정했으면 field는 요청에 쓴 이름이다 (Java 변수 이름이 아님)")
	void explicitlyNamedParameterUsesRequestName() {
		MvcTestResult result = mvc.get().uri("/test/errors/named-param?page_size=0").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("page_size");
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

		record PageQuery(@Min(1) int page) {
		}

		static final String RANGE_MESSAGE = "min must not be greater than max";

		/** 객체 전체(클래스 수준) 제약. 필드 하나가 아니라 두 필드의 관계를 본다. */
		@Target(ElementType.TYPE)
		@Retention(RetentionPolicy.RUNTIME)
		@Constraint(validatedBy = ValidRangeValidator.class)
		@interface ValidRange {

			String message() default RANGE_MESSAGE;

			Class<?>[] groups() default {};

			Class<? extends Payload>[] payload() default {};

		}

		public static final class ValidRangeValidator implements ConstraintValidator<ValidRange, RangeRequest> {

			@Override
			public boolean isValid(RangeRequest value, ConstraintValidatorContext context) {
				return value == null || value.min() <= value.max();
			}

		}

		@ValidRange
		record RangeRequest(int min, int max) {
		}

		@GetMapping("/query")
		String query(@Valid @ModelAttribute PageQuery query) {
			return "page " + query.page();
		}

		@PostMapping("/body")
		void body(@Valid @RequestBody CreateRequest request) {
		}

		@GetMapping("/param")
		String param(@RequestParam @Min(1) int page) {
			return "page " + page;
		}

		/** 파라미터에 제약이 있어 메서드 검증이 적용된다. 본문의 객체 전체 오류도 같은 예외로 온다. */
		@PostMapping("/object-and-param")
		void objectAndParam(@Valid @RequestBody RangeRequest request, @RequestParam @Min(1) int page) {
		}

		@GetMapping("/named-param")
		String namedParam(@RequestParam("page_size") @Min(1) int pageSize) {
			return "page size " + pageSize;
		}

		/** 반환값 제약을 어기는 서버 쪽 버그. 요청 오류가 아니다. */
		@GetMapping("/return-value")
		@NotNull
		String returnValue() {
			return null;
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
