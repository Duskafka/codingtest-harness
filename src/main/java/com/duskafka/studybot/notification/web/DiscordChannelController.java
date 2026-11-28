package com.duskafka.studybot.notification.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.notification.discord.DiscordChannels;
import com.duskafka.studybot.notification.discord.DiscordChannels.ChannelStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 디스코드 채널 웹훅 API (api.md 4.4, S14 만들 것 1·2). 권한: 방장. 웹훅 주소는 응답에 다시 내보내지 않는다. 응답 모양은 프론트(S16)가 쓰는
 * 계약이라 S14 PR 판단 기록에 '꼭 봐 주세요'로 남긴다. 설정을 읽는 GET은 api.md에 없어 만들지 않았다 (사람이 정함 2026-11-28 'PUT 응답에만
 * 담고 제안').
 */
@RestController
@RequiredArgsConstructor
class DiscordChannelController {

	static final String FIELD = "webhookUrl";

	private final DiscordChannels discordChannels;

	/**
	 * 등록·해제. 본문 {@code {"webhookUrl": "https://discord.com/api/webhooks/…"}}면 등록, {@code {"webhookUrl": null}}이면 해제다.
	 * {@code webhookUrl}이 없거나 문자열·null이 아니면 400이다. 권한(404·403)을 먼저 본다 (T19).
	 */
	@PutMapping("/api/studies/{studyId}/channels/discord")
	ChannelResponse change(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId,
			@RequestBody(required = false) @Nullable JsonNode body) {
		return ChannelResponse.from(this.discordChannels.change(studyId, loginUser.userId(), () -> webhookUrlOf(body)));
	}

	/** 테스트 게시. */
	@PostMapping("/api/studies/{studyId}/channels/discord/test")
	ChannelResponse test(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId) {
		return ChannelResponse.from(this.discordChannels.test(studyId, loginUser.userId()));
	}

	private static @Nullable String webhookUrlOf(@Nullable JsonNode body) {
		if (body == null || !body.isObject() || !body.has(FIELD)) {
			throw new BadRequestException("webhookUrl이 필요해요. 해제하려면 null을 보내요.");
		}
		JsonNode value = body.get(FIELD);
		if (value.isNull()) {
			return null;
		}
		if (!value.isString()) {
			throw new BadRequestException("webhookUrl은 문자열이거나 null이어야 해요.");
		}
		return value.asString();
	}

	/**
	 * 디스코드 채널 상태. 웹훅 주소는 담지 않는다.
	 *
	 * @param registered 웹훅 주소가 저장돼 있는지
	 * @param enabled 게시하는지 (해제했거나 웹훅이 지워졌으면 거짓)
	 * @param lastError 마지막 실패의 까닭 (예: {@code HTTP_404}). 없으면 {@code null}
	 */
	record ChannelResponse(boolean registered, boolean enabled, @Nullable String lastError) {

		static ChannelResponse from(ChannelStatus status) {
			return new ChannelResponse(status.registered(), status.enabled(), status.lastError());
		}

	}

}
