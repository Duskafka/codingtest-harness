package com.duskafka.studybot.study.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.study.service.InvitationService;
import com.duskafka.studybot.study.web.InvitationResponses.AcceptInvitationResponse;
import com.duskafka.studybot.study.web.InvitationResponses.CreatedInvitationResponse;
import com.duskafka.studybot.study.web.InvitationResponses.InvitationPreviewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대 API (api.md 4.4, S05 만들 것 5·6). 미리보기만 공개이고 나머지는 로그인이 필요하다 ({@code SecurityConfiguration}).
 * 철회 {@code DELETE /api/studies/{id}/invitations/{invitationId}}는 api.md 4.4에 없어 PR '스펙 변경 제안'에 올렸다
 * (사람이 만들기로 정함, 2026-11-23).
 */
@RestController
@RequiredArgsConstructor
class InvitationController {

	private final InvitationService invitationService;

	/** 초대 만들기 (권한: 방장). 201. 토큰 원문은 이 응답에만 있다. */
	@PostMapping("/api/studies/{studyId}/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	CreatedInvitationResponse create(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId,
			@Valid @RequestBody(required = false) @Nullable CreateInvitationRequest request) {
		int maxUses = (request != null ? request : new CreateInvitationRequest(null)).maxUsesOrDefault();
		return CreatedInvitationResponse.from(this.invitationService.create(studyId, loginUser.userId(), maxUses));
	}

	/** 초대 철회 (권한: 방장). 204. */
	@DeleteMapping("/api/studies/{studyId}/invitations/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revoke(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId,
			@PathVariable long invitationId) {
		this.invitationService.revoke(studyId, loginUser.userId(), invitationId);
	}

	/** 초대 미리보기 (권한: 공개). 없는 토큰 404, 만료·철회·소진 410. */
	@GetMapping("/api/invitations/{token}")
	InvitationPreviewResponse preview(@PathVariable String token) {
		return InvitationPreviewResponse.from(this.invitationService.preview(token));
	}

	/** 초대 수락 (권한: 로그인). 이미 멤버여도 200과 같은 본문이다. */
	@PostMapping("/api/invitations/{token}/accept")
	AcceptInvitationResponse accept(@AuthenticationPrincipal LoginUser loginUser, @PathVariable String token) {
		return AcceptInvitationResponse.from(this.invitationService.accept(token, loginUser.userId()));
	}

}
