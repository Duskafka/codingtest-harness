package com.duskafka.studybot.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 문제 식별자 (S03, glossary {@code ProblemRef}). 키는 (플랫폼, 번호)이고 번호는 정규형 문자열이다. */
class ProblemRefTest {

	@Test
	@DisplayName("S03: 플랫폼과 번호가 모두 같아야 같은 문제다")
	void equality() {
		assertThat(new ProblemRef(Platform.BOJ, "1000")).isEqualTo(new ProblemRef(Platform.BOJ, "1000"))
			.isNotEqualTo(new ProblemRef(Platform.PROGRAMMERS, "1000"))
			.isNotEqualTo(new ProblemRef(Platform.BOJ, "1001"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "0", "0001", "12a", "１２", "-1", "1 ", "two-sum" })
	@DisplayName("S03: 번호는 0으로 시작하지 않는 ASCII 숫자만")
	void rejectsNonCanonicalNumber(String problemNo) {
		assertThatIllegalArgumentException().isThrownBy(() -> new ProblemRef(Platform.BOJ, problemNo));
	}

	@Test
	@DisplayName("S03: 플랫폼과 번호는 null일 수 없다")
	void rejectsNull() {
		assertThatNullPointerException().isThrownBy(() -> new ProblemRef(null, "1000"));
		assertThatNullPointerException().isThrownBy(() -> new ProblemRef(Platform.BOJ, null));
	}

}
