package com.duskafka.studybot.verification;

import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 파일 경로 하나의 원본과, {@code /}로 나눈 조각마다 정규화한 값 (5.4 '경로 정규화').
 *
 * <p>정규화는 조각을 나눈 뒤에 한다. 백준허브는 제목의 {@code /}를 전각 {@code ／}로 바꿔 쓰는데, 나누기 전에 전각을 접으면
 * 제목 안의 빗금이 경로 구분자가 되기 때문이다.
 *
 * <p>숨김 경로나 문서 파일인지는 정규화하기 전 조각({@link #rawSegments()})으로 판단한다. 백준허브는 제목의 {@code .}을 전각
 * {@code ．}으로 쓰므로, 접은 뒤에 보면 제목이 {@code .}으로 시작하는 문제가 숨김 파일로 보인다.
 *
 * @param raw 원본 경로
 * @param rawSegments 원본 경로를 {@code /}로 나눈 조각
 * @param segments 정규화한 조각. 마지막이 파일 이름
 */
record PathSegments(String raw, List<String> rawSegments, List<String> segments) {

	private static final int FULL_WIDTH_FIRST = 0xFF01;

	private static final int FULL_WIDTH_LAST = 0xFF5E;

	/** 전각 ASCII(U+FF01~U+FF5E)와 ASCII(U+0021~U+007E)의 거리. */
	private static final int FULL_WIDTH_OFFSET = 0xFEE0;

	static PathSegments of(String rawPath) {
		Objects.requireNonNull(rawPath, "path");
		List<String> rawSegments = List.of(rawPath.split("/", -1));
		return new PathSegments(rawPath, rawSegments, rawSegments.stream().map(PathSegments::normalize).toList());
	}

	/** 정규화한 파일 이름. */
	String fileName() {
		return segments.getLast();
	}

	/** 정규화하기 전 파일 이름. */
	String rawFileName() {
		return rawSegments.getLast();
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
