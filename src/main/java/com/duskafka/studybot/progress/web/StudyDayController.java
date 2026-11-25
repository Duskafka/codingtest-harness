package com.duskafka.studybot.progress.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.progress.service.StudyDayStatusService;
import com.duskafka.studybot.progress.web.StudyDayResponses.StudyDayStatusResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오늘 현황과 특정 날 상세 API (api.md 4.4, S10 만들 것 1·2). 권한: 멤버. 권한 검사는 서비스가 {@code StudyAccess}로 한다.
 */
@RestController
@RequestMapping("/api/studies/{studyId}")
@RequiredArgsConstructor
class StudyDayController {

	private final StudyDayStatusService statusService;

	/** 오늘 현황: 지금 시각이 속한 스터디 일의 멤버별 완료·남음·면제와 최근 풀이. */
	@GetMapping("/today")
	StudyDayStatusResponse today(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId) {
		return StudyDayStatusResponse.from(this.statusService.today(studyId, loginUser.userId()));
	}

	/** 특정 날 상세. 날짜는 ISO 8601 날짜({@code 2026-11-20})이고, 아직 오지 않은 날이면 400이다. */
	@GetMapping("/days/{date}")
	StudyDayStatusResponse day(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return StudyDayStatusResponse.from(this.statusService.day(studyId, loginUser.userId(), date));
	}

}
