package com.duskafka.studybot.verification.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.verification.ProblemRef;
import com.duskafka.studybot.verification.service.MySolveService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 풀이 API (api.md 4.4, S10 만들 것 3·4). 권한: 로그인(목록), 본인(지정). 풀이 기록은 로그인한 사용자의 것만 다룬다. */
@RestController
@RequestMapping("/api/me/solves")
@RequiredArgsConstructor
class MySolveController {

	/** 목록 한 페이지의 최대 크기. 목록 API는 전부 페이지네이션한다 (api.md 4.4, Supabase egress). 스터디 목록과 같다. */
	static final int MAX_PAGE_SIZE = 50;

	private final MySolveService mySolveService;

	/**
	 * 내 풀이 (확인 대기 포함). 수신 시각이 늦은 것부터.
	 *
	 * @param from 이 시각부터 (포함). ISO 8601 날짜·시각과 오프셋 (예: {@code 2026-11-20T00:00:00+09:00}). 없으면 처음부터
	 * @param to 이 시각 전까지 (뺌). 형식은 {@code from}과 같다. 없으면 끝까지
	 */
	@GetMapping
	PagedModel<MySolveResponse> list(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable OffsetDateTime to,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		return new PagedModel<>(this.mySolveService
			.mySolves(loginUser.userId(), from == null ? null : from.toInstant(), to == null ? null : to.toInstant(), page,
					size)
			.map(MySolveResponse::from));
	}

	/**
	 * 확인 대기 풀이에 문제를 지정한다. 원래 수신 시각의 스터디 일에 반영한다 (R6). 남의 풀이나 없는 풀이는 404, 확인 대기가 아니거나 그날이
	 * 이미 판정됐으면 409.
	 */
	@PatchMapping("/{solveId}")
	MySolveResponse identify(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long solveId,
			@Valid @RequestBody IdentifyProblemRequest request) {
		ProblemRef problem = new ProblemRef(request.platform(), request.problemNo());
		return MySolveResponse.from(this.mySolveService.identify(loginUser.userId(), solveId, problem));
	}

}
