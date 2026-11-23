package com.duskafka.studybot.github.webhook;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 수신함 {@code last_error}에 남길 처리 실패 설명.
 *
 * <p>예외 메시지는 남기지 않고 예외 종류만 남긴다 (사람이 정함, 2026-11-23 S06 세션 "메시지 대신 예외 종류만").
 * Jackson 변환 오류 메시지에는 payload의 값(커밋 작성자 이메일 등)이, PostgreSQL 오류 메시지에는 키 값이 들어갈 수 있어서다
 * (security.md 9.8, R16). 전체 메시지와 스택은 서버 로그에만 남고, 로그는 전달 ID로 찾는다.
 *
 * <ul>
 * <li>원인 사슬을 바깥에서 안쪽 순서로 클래스 이름만 {@code " ← "}로 잇는다. {@value #MAX_DEPTH}단계를 넘으면 줄임표로 끝낸다.</li>
 * <li>{@link SQLException}이면 SQLState를 붙인다 (예: {@code 23505} 유니크 위반). 값이 아니라 오류 분류라 남겨도 된다.</li>
 * <li>{@link WebhookHandlingException}이면 그 메시지를 붙인다. 처리기가 남겨도 된다고 준 문장이다.</li>
 * </ul>
 */
final class WebhookFailureDescription {

	static final int MAX_DEPTH = 5;

	private static final String SEPARATOR = " ← ";

	private WebhookFailureDescription() {
	}

	static String of(Throwable failure) {
		List<String> parts = new ArrayList<>();
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Throwable current = failure;
		while (current != null && seen.add(current)) {
			if (parts.size() == MAX_DEPTH) {
				parts.add("…");
				break;
			}
			parts.add(describeOne(current));
			current = current.getCause();
		}
		return String.join(SEPARATOR, parts);
	}

	private static String describeOne(Throwable throwable) {
		String name = throwable.getClass().getName();
		if (throwable instanceof WebhookHandlingException && throwable.getMessage() != null) {
			return name + ": " + throwable.getMessage();
		}
		if (throwable instanceof SQLException sql && sql.getSQLState() != null) {
			return name + " (SQLState " + sql.getSQLState() + ")";
		}
		return name;
	}

}
