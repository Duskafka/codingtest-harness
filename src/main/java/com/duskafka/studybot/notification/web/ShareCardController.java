package com.duskafka.studybot.notification.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.notification.share.ShareCards;
import com.duskafka.studybot.notification.share.ShareCards.ShareCard;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공유 카드 API (api.md 4.4, S14 만들 것 6). 권한: 멤버. 응답 모양은 프론트(S16)가 쓰는 계약이라 S14 PR 판단 기록에 '꼭 봐 주세요'로 남긴다.
 */
@RestController
@RequiredArgsConstructor
class ShareCardController {

	private final ShareCards shareCards;

	/** {@code type}이 없거나 {@code today}가 아니면 400. 권한(404)을 먼저 본다. */
	@GetMapping("/api/studies/{studyId}/share-card")
	ShareCardResponse card(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long studyId,
			@RequestParam(required = false) @Nullable String type) {
		return ShareCardResponse.from(this.shareCards.card(studyId, loginUser.userId(), type));
	}

	/**
	 * 공유 카드.
	 *
	 * @param type {@code today}
	 * @param studyId 스터디 ID
	 * @param studyDay 오늘 스터디 일
	 * @param title 카드 제목 (예: {@code [코테 스터디] 11/20 인증 현황})
	 * @param summary 요약 한 줄 (예: {@code 완료 5명 · 남음 2명 (민수, 지은) · 마감 24:00}). 벌금 금액은 없다
	 * @param url 오늘 현황 화면의 절대 주소 (로그인한 멤버만 볼 수 있음)
	 * @param text '텍스트 복사'용: 제목, 요약, {@code 현황 보기: 주소} 세 줄
	 * @param counts 완료·남음·면제 수 (판정 대상이 아닌 멤버는 세지 않음)
	 */
	record ShareCardResponse(String type, long studyId, LocalDate studyDay, String title, String summary, String url,
			String text, Counts counts) {

		static ShareCardResponse from(ShareCard card) {
			return new ShareCardResponse(card.type(), card.studyId(), card.studyDay(), card.card().title(),
					card.card().summary(), card.card().url(), card.card().text(),
					new Counts(card.card().done(), card.card().remaining(), card.card().exempt()));
		}

	}

	record Counts(int done, int remaining, int exempt) {

	}

}
