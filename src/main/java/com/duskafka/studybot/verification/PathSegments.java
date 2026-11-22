package com.duskafka.studybot.verification;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 파일 경로 하나의 원본과, {@code /}로 나눈 조각마다 정규화한 값 (5.4 '경로 정규화').
 *
 * <p>정규화는 조각을 나눈 뒤에 한다. 백준허브는 제목의 {@code /}를 전각 {@code ／}로 바꿔 쓰는데, 나누기 전에 전각을 접으면
 * 제목 안의 빗금이 경로 구분자가 되기 때문이다.
 *
 * @param raw 원본 경로
 * @param segments 정규화한 조각. 마지막이 파일 이름
 */
record PathSegments(String raw, List<String> segments) {

	private static final int FULL_WIDTH_FIRST = 0xFF01;

	private static final int FULL_WIDTH_LAST = 0xFF5E;

	/** 전각 ASCII(U+FF01~U+FF5E)와 ASCII(U+0021~U+007E)의 거리. */
	private static final int FULL_WIDTH_OFFSET = 0xFEE0;

	static PathSegments of(String rawPath) {
		Objects.requireNonNull(rawPath, "path");
		List<String> segments = Arrays.stream(rawPath.split("/", -1)).map(PathSegments::normalize).toList();
		return new PathSegments(rawPath, segments);
	}

	String fileName() {
		return segments.getLast();
	}

	/** 끝에서 {@code offset}번째 조각. 0은 파일 이름, 1은 바로 위 폴더다. 없으면 {@code null}. */
	@Nullable String fromEnd(int offset) {
		int index = segments.size() - 1 - offset;
		return index >= 0 ? segments.get(index) : null;
	}

	/**
	 * 조각 하나를 정규화한다. 유니코드 NFC로 합치고(macOS에서 NFD로 온 한글), 전각 ASCII는 반각으로,
	 * 공백류(U+00A0, U+2000~U+200A, U+3000)는 ASCII 공백으로 바꾼다. 백준허브는 제목의 공백을 U+2005로 쓴다.
	 *
	 * <p>NFKC는 쓰지 않는다. 전각 접기 말고도 ①→1, ㈜→(주)처럼 스펙(5.4)이 말하지 않은 변환까지 하기 때문이다.
	 */
	static String normalize(String segment) {
		String composed = Normalizer.normalize(segment, Normalizer.Form.NFC);
		StringBuilder folded = new StringBuilder(composed.length());
		composed.codePoints().map(PathSegments::fold).forEach(folded::appendCodePoint);
		return folded.toString();
	}

	private static int fold(int codePoint) {
		if (codePoint >= FULL_WIDTH_FIRST && codePoint <= FULL_WIDTH_LAST) {
			return codePoint - FULL_WIDTH_OFFSET;
		}
		if (codePoint == 0x00A0 || codePoint == 0x3000 || (codePoint >= 0x2000 && codePoint <= 0x200A)) {
			return ' ';
		}
		return codePoint;
	}

}
