package com.duskafka.studybot.verification.web;

import com.duskafka.studybot.verification.Platform;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * 수동 인증 요청 (R7, S11 만들 것 1).
 *
 * @param studyDay 인증할 스터디 일 (ISO 8601 날짜). 오늘, 또는 유예 중인 전날
 * @param platform 플랫폼 ({@code BOJ}, {@code PROGRAMMERS}, {@code LEETCODE})
 * @param problemNo 문제 번호. 0으로 시작하지 않는 숫자 문자열 ({@code ProblemRef} 정규형), 10자리까지
 * @param evidenceUrl 증거 링크 (선택). {@code http}·{@code https}만, 공백 없이 2000자까지. 서버는 접속하지 않는다 (9.7)
 * @param memo 메모 (선택). 500자까지
 */
record SelfReportRequest(@NotNull LocalDate studyDay, @NotNull Platform platform,
		@NotNull @Pattern(regexp = "[1-9][0-9]*") @Size(max = 10) String problemNo,
		@Nullable @Size(max = 2000) @Pattern(regexp = "(?i)https?://\\S+") String evidenceUrl,
		@Nullable @Size(max = 500) String memo) {

}
