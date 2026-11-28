package com.duskafka.studybot.notification.share;

import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.notification.card.Cards;
import com.duskafka.studybot.notification.card.Cards.StatusCard;
import com.duskafka.studybot.notification.link.PublicLinks;
import com.duskafka.studybot.progress.StudyDayBoards;
import com.duskafka.studybot.progress.StudyDayBoards.StudyDayBoard;
import com.duskafka.studybot.study.StudyAccess;
import com.duskafka.studybot.study.StudyMode;
import com.duskafka.studybot.study.StudyProfiles;
import com.duskafka.studybot.study.StudyProfiles.StudyProfile;
import java.time.LocalDate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 공유 카드 (S14 만들 것 6, api.md 4.4 {@code GET /api/studies/{id}/share-card?type=today}, 5.9). 방장이 카카오톡 공유(S16)나 '텍스트
 * 복사'로 단톡방에 보낼 내용이다.
 *
 * <ul>
 * <li>권한: 멤버. 멤버가 아니면 404다 (T19). {@code type} 검사보다 먼저 한다.</li>
 * <li>혼자 쓰기 스터디는 공유가 없어 409 {@code SOLO_STUDY_NO_SHARING}이다 (R15, 사람이 정함 2026-11-28 "둘 다 거절").</li>
 * <li>{@code type}은 {@value #TODAY}만 받는다. 주간 카드는 P2다. 그 밖이면 400이다.</li>
 * <li>벌금 금액은 넣지 않는다 (5.9 '기본으로 빼고 방장이 켤 때만', 켜는 방법은 S14 PR 열린 질문, 사람이 정함 2026-11-28 'S14는 늘 뺌').</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ShareCards {

	public static final String TODAY = "today";

	private final StudyAccess studyAccess;

	private final StudyProfiles studyProfiles;

	private final StudyDayBoards boards;

	private final PublicLinks links;

	/** 공유 카드. */
	public ShareCard card(long studyId, long userId, @Nullable String type) {
		this.studyAccess.requireMember(studyId, userId);
		StudyProfile profile = this.studyProfiles.of(studyId);
		if (profile.mode() == StudyMode.SOLO) {
			throw new ConflictException("SOLO_STUDY_NO_SHARING", "혼자 쓰기 스터디는 공유 기능이 없어요.");
		}
		if (!TODAY.equals(type)) {
			throw new BadRequestException("type은 today만 받아요.");
		}
		StudyDayBoard board = this.boards.of(studyId, null);
		StatusCard card = Cards.status(profile.name(), board, profile.zoneId(), this.links.today(studyId));
		return new ShareCard(TODAY, studyId, board.studyDay(), card);
	}

	/**
	 * 공유 카드 하나.
	 *
	 * @param type 카드 종류 ({@value #TODAY})
	 * @param studyId 스터디 ID
	 * @param studyDay 카드가 보여 주는 스터디 일 (오늘)
	 * @param card 제목·요약·링크·수
	 */
	public record ShareCard(String type, long studyId, LocalDate studyDay, StatusCard card) {

		public ShareCard {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(studyDay, "studyDay");
			Objects.requireNonNull(card, "card");
		}

	}

}
