package com.duskafka.studybot.common.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 오류 응답을 RFC 9457 {@code application/problem+json}({@link ProblemDetail})으로 통일한다
 * (docs/spec/api.md 4.4, security.md 9.7).
 *
 * <ul>
 * <li>Spring MVC 예외(없는 경로 404, 형식 오류 400, 405 등)와 {@link org.springframework.web.ErrorResponseException}
 * (이 패키지의 {@link NotFoundException} 등)은 {@link ResponseEntityExceptionHandler}가 처리한다.</li>
 * <li>검증 실패(400)에는 {@code errors: [{field, message}]}를 더한다.</li>
 * <li>처리하지 못한 예외는 500으로 바꾸고, 응답에는 상태와 제목만 담는다. 예외 이름·메시지·스택은 서버 로그에만 남긴다.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 검증 실패 항목 목록을 담는 ProblemDetail 확장 속성 이름. */
	static final String ERRORS_PROPERTY = "errors";

	/** 바인딩 실패에 맞는 메시지가 없을 때 쓰는 문장. 검증 메시지 언어는 아직 정하지 않았다 (PR 열린 질문). */
	static final String INVALID_VALUE_MESSAGE = "Invalid value";

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Locale locale = request.getLocale();
		List<InvalidField> errors = new ArrayList<>();
		ex.getBindingResult()
			.getFieldErrors()
			.forEach(error -> errors.add(new InvalidField(error.getField(), resolveFieldError(error, locale))));
		ex.getBindingResult()
			.getGlobalErrors()
			.forEach(error -> errors.add(new InvalidField(null, resolve(error, locale))));
		ProblemDetail body = ex.getBody();
		body.setProperty(ERRORS_PROPERTY, errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Locale locale = request.getLocale();
		List<InvalidField> errors = new ArrayList<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			if (result instanceof ParameterErrors parameterErrors) {
				for (FieldError error : parameterErrors.getFieldErrors()) {
					errors.add(new InvalidField(error.getField(), resolveFieldError(error, locale)));
				}
				parameterErrors.getGlobalErrors()
					.forEach(error -> errors.add(new InvalidField(parameterName(result), resolve(error, locale))));
			}
			else {
				for (MessageSourceResolvable error : result.getResolvableErrors()) {
					errors.add(new InvalidField(parameterName(result), resolve(error, locale)));
				}
			}
		}
		ex.getCrossParameterValidationResults()
			.forEach(error -> errors.add(new InvalidField(null, resolve(error, locale))));
		ProblemDetail body = ex.getBody();
		body.setProperty(ERRORS_PROPERTY, errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/** 처리하지 못한 예외. 응답에 내부 정보를 싣지 않는다 (security.md 9.7). */
	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		log.error("처리하지 못한 예외: {}", request.getDescription(false), ex);
		ProblemDetail body = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	/**
	 * 바인딩 실패(타입 변환 실패 등)의 기본 메시지에는 Java 타입 이름과 변환 예외 메시지가 들어 있다
	 * (예: "Failed to convert value of type 'java.lang.String' to required type 'int'; For input string: ...").
	 * 그래서 오류 코드({@code typeMismatch} 등)로 메시지를 찾고, 없으면 일반 문장을 쓴다.
	 */
	private String resolveFieldError(FieldError error, Locale locale) {
		if (!error.isBindingFailure()) {
			return resolve(error, locale);
		}
		return resolve(new DefaultMessageSourceResolvable(error.getCodes(), INVALID_VALUE_MESSAGE), locale);
	}

	private String resolve(MessageSourceResolvable error, Locale locale) {
		MessageSource messageSource = getMessageSource();
		if (messageSource != null) {
			return messageSource.getMessage(error, locale);
		}
		String message = error.getDefaultMessage();
		return message != null ? message : "";
	}

	private static String parameterName(ParameterValidationResult result) {
		String name = result.getMethodParameter().getParameterName();
		String base = name != null ? name : "arg" + result.getMethodParameter().getParameterIndex();
		if (result.getContainerIndex() != null) {
			return base + "[" + result.getContainerIndex() + "]";
		}
		if (result.getContainerKey() != null) {
			return base + "[" + result.getContainerKey() + "]";
		}
		return base;
	}

	/**
	 * 검증 실패 항목 하나.
	 * @param field 틀린 필드나 요청 파라미터 이름. 객체 전체나 여러 파라미터에 걸친 오류면 {@code null}
	 * @param message 틀린 이유
	 */
	public record InvalidField(@Nullable String field, String message) {
	}

}
