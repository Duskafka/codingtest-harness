package com.duskafka.studybot.verification;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 연결할 때 지정한 경로 접두사 ({@code linked_repositories.path_prefix}, 5.4 흐름도 '연결할 때 지정한 경로 접두사 안의 파일?').
 * 식별하지 못한 풀이 파일이 이 안에 있으면 확인 대기로 남긴다 (S08 만들 것 3).
 *
 * <ul>
 * <li>비어 있으면({@code null}, 공백, 빗금뿐) 레포 전체가 범위다 (사람이 정함, 2026-11-24 S08 세션 "레포 전체로 봄").
 * P1에는 접두사를 정하는 API가 없어 모든 연결이 이 경우다.</li>
 * <li>경로 조각 단위로 비교한다. {@code algorithm}은 {@code algorithm/…} 안의 파일과 맞고 {@code algorithms/…}와는 맞지 않는다.
 * 앞뒤 빗금과 빈 조각은 무시한다.</li>
 * <li>조각은 파서와 같은 방법으로 정규화한 뒤 비교한다 ({@link PathSegments#normalize}: NFC, 전각 접기). 대소문자는 구분한다.</li>
 * </ul>
 */
final class PathPrefix {

	private static final PathPrefix WHOLE_REPOSITORY = new PathPrefix(List.of());

	private final List<String> segments;

	private PathPrefix(List<String> segments) {
		this.segments = segments;
	}

	static PathPrefix of(@Nullable String prefix) {
		if (prefix == null) {
			return WHOLE_REPOSITORY;
		}
		List<String> segments = Arrays.stream(prefix.split("/"))
			.filter(segment -> !segment.isBlank())
			.map(PathSegments::normalize)
			.toList();
		return segments.isEmpty() ? WHOLE_REPOSITORY : new PathPrefix(segments);
	}

	/** 이 파일이 접두사 안에 있는지. 접두사와 이름이 같은 파일 자체는 안에 있지 않다. */
	boolean contains(String rawPath) {
		if (segments.isEmpty()) {
			return true;
		}
		List<String> path = PathSegments.of(rawPath).segments();
		return path.size() > segments.size() && path.subList(0, segments.size()).equals(segments);
	}

}
