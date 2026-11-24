package com.duskafka.studybot.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** S08 만들 것 3: 확인 대기는 연결할 때 지정한 경로 접두사 안의 파일만 (5.4 흐름도). */
class PathPrefixTest {

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "  ", "/", "//" })
	@DisplayName("S08: 접두사가 비어 있으면 레포 전체가 범위다 (사람이 정함, 2026-11-24 \"레포 전체로 봄\")")
	void emptyPrefixContainsEveryPath(String prefix) {
		PathPrefix whole = PathPrefix.of(prefix);

		assertThat(whole.contains("Main.java")).isTrue();
		assertThat(whole.contains("a/b/c/Main.java")).isTrue();
	}

	@Test
	@DisplayName("S08: 접두사는 경로 조각 단위로 본다 (algorithm은 algorithms/와 맞지 않음)")
	void prefixMatchesWholeSegments() {
		PathPrefix prefix = PathPrefix.of("algorithm");

		assertThat(prefix.contains("algorithm/Main.java")).isTrue();
		assertThat(prefix.contains("algorithm/codetree/별 찍기/Main.java")).isTrue();
		assertThat(prefix.contains("algorithms/Main.java")).isFalse();
		assertThat(prefix.contains("notes/algorithm/Main.java")).isFalse();
		assertThat(prefix.contains("algorithm")).isFalse();
	}

	@Test
	@DisplayName("S08: 여러 조각 접두사, 앞뒤 빗금은 무시한다")
	void multiSegmentPrefixIgnoresSurroundingSlashes() {
		PathPrefix prefix = PathPrefix.of("/study/boj/");

		assertThat(prefix.contains("study/boj/1000/Main.java")).isTrue();
		assertThat(prefix.contains("study/Main.java")).isFalse();
		assertThat(prefix.contains("study/bojx/Main.java")).isFalse();
	}

	@Test
	@DisplayName("S08: 대소문자는 구분한다 (Git 경로는 대소문자를 구분)")
	void prefixIsCaseSensitive() {
		assertThat(PathPrefix.of("Algorithm").contains("algorithm/Main.java")).isFalse();
	}

	@Test
	@DisplayName("S08: 한글은 NFC로 맞춰 비교한다 (macOS에서 NFD로 온 경로, 5.4 '주의할 것')")
	void hangulIsComparedInNfc() {
		String nfd = Normalizer.normalize("알고리즘", Normalizer.Form.NFD);

		assertThat(PathPrefix.of("알고리즘").contains(nfd + "/풀이.java")).isTrue();
		assertThat(PathPrefix.of(nfd).contains("알고리즘/풀이.java")).isTrue();
	}

}
