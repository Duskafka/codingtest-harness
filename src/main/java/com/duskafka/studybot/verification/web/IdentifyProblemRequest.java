package com.duskafka.studybot.verification.web;

import com.duskafka.studybot.verification.Platform;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 확인 대기 풀이에 지정할 문제 (S10 만들 것 4). 문제 식별자는 (플랫폼, 번호)다 (R6).
 *
 * @param platform 플랫폼 ({@code BOJ}, {@code PROGRAMMERS}, {@code LEETCODE})
 * @param problemNo 문제 번호. 0으로 시작하지 않는 숫자 문자열 ({@code ProblemRef} 정규형), 10자리까지
 */
record IdentifyProblemRequest(@NotNull Platform platform,
		@NotNull @Pattern(regexp = "[1-9][0-9]*") @Size(max = 10) String problemNo) {

}
