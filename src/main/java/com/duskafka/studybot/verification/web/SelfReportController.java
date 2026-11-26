package com.duskafka.studybot.verification.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.verification.ProblemRef;
import com.duskafka.studybot.verification.service.NewSelfReport;
import com.duskafka.studybot.verification.service.SelfReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수동 인증 API (api.md 4.4, S11 만들 것 1·3). 권한: 멤버(제출), 본인(철회). 권한 검사는 서비스와 progress가 한다.
 */
@RestController
@RequestMapping("/api/studies/{studyId}/self-reports")
@RequiredArgsConstructor
class SelfReportController {

	private final SelfReportService selfReportService;

	/** 수동 인증 제출 (바로 인정). 201과 수동 인증. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	SelfReportResponse submit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId,
			@Valid @RequestBody SelfReportRequest request) {
		NewSelfReport report = new NewSelfReport(request.studyDay(),
				new ProblemRef(request.platform(), request.problemNo()), request.evidenceUrl(), request.memo());
		return SelfReportResponse.from(this.selfReportService.submit(studyId, loginUser.userId(), report));
	}

	/** 수동 인증 철회 (판정 전). 204. */
	@DeleteMapping("/{reportId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId,
			@PathVariable long reportId) {
		this.selfReportService.withdraw(studyId, loginUser.userId(), reportId);
	}

}
