package com.duskafka.studybot.study.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.study.service.MembershipService;
import com.duskafka.studybot.study.service.StudyCreationService;
import com.duskafka.studybot.study.service.StudyQueryService;
import com.duskafka.studybot.study.service.StudySettingsService;
import com.duskafka.studybot.study.web.StudyResponses.StudyDetailResponse;
import com.duskafka.studybot.study.web.StudyResponses.StudySummaryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스터디 API (api.md 4.4, S05 만들 것 1·2·3·7). 권한 검사는 서비스가 {@code StudyAccess}로 한다.
 * 규칙 변경 {@code PUT /api/studies/{id}/rules}는 OD-08이 정해지기 전에는 만들지 않는다 (S05 만들 것 4).
 */
@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
class StudyController {

	/** 목록 한 페이지의 최대 크기. 목록 API는 전부 페이지네이션한다 (api.md 4.4, Supabase egress). */
	static final int MAX_PAGE_SIZE = 50;

	private final StudyCreationService creationService;

	private final StudyQueryService queryService;

	private final StudySettingsService settingsService;

	private final MembershipService membershipService;

	/** 스터디 만들기 (권한: 로그인). 201과 스터디 상세. */
	@PostMapping
	ResponseEntity<StudyDetailResponse> create(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody CreateStudyRequest request) {
		long studyId = this.creationService.create(loginUser.userId(), request.toNewStudy());
		return ResponseEntity.created(URI.create("/api/studies/" + studyId))
			.body(StudyDetailResponse.from(this.queryService.detail(studyId, loginUser.userId())));
	}

	/** 내 스터디 목록 (권한: 로그인). 최근에 가입한 것부터. */
	@GetMapping
	PagedModel<StudySummaryResponse> list(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		return new PagedModel<>(
				this.queryService.myStudies(loginUser.userId(), page, size).map(StudySummaryResponse::from));
	}

	/** 스터디 상세 (권한: 멤버). */
	@GetMapping("/{studyId}")
	StudyDetailResponse detail(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId) {
		return StudyDetailResponse.from(this.queryService.detail(studyId, loginUser.userId()));
	}

	/** 이름 변경과 방장 위임 (권한: 방장). 바뀐 스터디 상세. */
	@PatchMapping("/{studyId}")
	StudyDetailResponse update(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId,
			@Valid @RequestBody UpdateStudyRequest request) {
		this.settingsService.update(studyId, loginUser.userId(), request.name(), request.ownerUserId());
		return StudyDetailResponse.from(this.queryService.detail(studyId, loginUser.userId()));
	}

	/** 스터디 탈퇴 (권한: 멤버). 방장은 위임한 뒤에만 (409). */
	@DeleteMapping("/{studyId}/members/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void leave(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId) {
		this.membershipService.leave(studyId, loginUser.userId());
	}

}
