package com.duskafka.studybot.progress.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.progress.service.ExemptionResult;
import com.duskafka.studybot.progress.service.ExemptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 면제권 사용·취소 API (api.md 4.4, S11 만들 것 5·6). 권한: 멤버(사용), 본인(취소: 내 멤버 일만 고친다). 권한 검사는 서비스가 한다.
 *
 * <p>둘 다 200과 지금 상태(남은 개수 포함)를 준다. 같은 날 두 번 써도, 쓰지 않은 날을 취소해도 200이다 (초대 수락처럼 같은 요청을 다시
 * 보내도 같은 결과). 취소도 남은 개수를 보여 줘야 해서 204 대신 200이다 (5.7 '남은 면제권 N개', S11 만들 것 6).
 */
@RestController
@RequestMapping("/api/studies/{studyId}/exemptions")
@RequiredArgsConstructor
class ExemptionController {

	private final ExemptionService exemptionService;

	/** 오늘 면제권을 쓴다. 날짜를 받아 오늘인지 확인한다 (경계 직후에 도착한 요청이 다음 날에 쓰이지 않게, OD-09). */
	@PostMapping
	ExemptionResponse use(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId,
			@Valid @RequestBody UseExemptionRequest request) {
		return ExemptionResponse.from(this.exemptionService.use(studyId, loginUser.userId(), request.studyDay()));
	}

	/** 오늘 쓴 면제권을 취소한다. 기준 시각이 지난 날이면 400. */
	@DeleteMapping("/{studyDay}")
	ExemptionResponse cancel(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDay) {
		return ExemptionResponse.from(this.exemptionService.cancel(studyId, loginUser.userId(), studyDay));
	}

	/**
	 * 면제권을 쓸 날.
	 *
	 * @param studyDay 스터디 일 (ISO 8601 날짜). 오늘이어야 한다 (OD-09)
	 */
	record UseExemptionRequest(@NotNull LocalDate studyDay) {
	}

	/**
	 * 면제권 상태. 프론트(S16)가 쓰는 계약이라 S11 PR 판단 기록에 '꼭 봐 주세요'로 남긴다.
	 *
	 * @param studyDay 스터디 일
	 * @param exemptionUsed 그날 면제권을 쓴 상태인지
	 * @param exemptionsPerSeason 기수당 면제권
	 * @param exemptionsRemaining 이번 기수에 남은 면제권. 성공으로 판정되어 돌려받은 것은 남은 것으로 센다. 그날이 기수 밖이면 {@code null}
	 */
	record ExemptionResponse(LocalDate studyDay, boolean exemptionUsed, int exemptionsPerSeason,
			@Nullable Integer exemptionsRemaining) {

		static ExemptionResponse from(ExemptionResult result) {
			return new ExemptionResponse(result.studyDay(), result.exemptionUsed(), result.exemptionsPerSeason(),
					result.exemptionsRemaining());
		}

	}

}
