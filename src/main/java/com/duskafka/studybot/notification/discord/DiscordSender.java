package com.duskafka.studybot.notification.discord;

import com.duskafka.studybot.notification.card.Cards;
import com.duskafka.studybot.notification.dispatch.NotificationSender;
import com.duskafka.studybot.notification.dispatch.SendResult;
import com.duskafka.studybot.notification.link.PublicLinks;
import com.duskafka.studybot.notification.outbox.NotificationChannel;
import com.duskafka.studybot.notification.outbox.NotificationType;
import com.duskafka.studybot.notification.persistence.NotificationQueue.ClaimedNotification;
import com.duskafka.studybot.notification.persistence.StudyChannel;
import com.duskafka.studybot.notification.persistence.StudyChannelRepository;
import com.duskafka.studybot.notification.result.DailyResultFallback;
import com.duskafka.studybot.progress.StudyDayBoards;
import com.duskafka.studybot.progress.StudyDayBoards.StudyDayBoard;
import com.duskafka.studybot.study.StudyProfiles;
import com.duskafka.studybot.study.StudyProfiles.StudyProfile;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * 디스코드 스터디 채널로 알림을 보낸다 (S14 만들 것 3·4·5, 5.8 '디스코드'). 발송기가 트랜잭션 밖에서 부른다.
 *
 * <ul>
 * <li>보낼 때 채널 행을 읽어 웹훅 주소를 푼다. 꺼졌거나 없으면 보내지 않는다 (발송기가 직전에 SKIPPED로 거르지만 그 뒤에 꺼진 경우).</li>
 * <li>임베드는 보낼 때 DB에서 만든다: 현황({@code DISCORD_STATUS})은 그날 멤버별 상태, 결과({@code DAILY_RESULT})는 그날 판정 결과와 실패한
 * 멤버 이름이다. 그래서 outbox payload에 이름을 남기지 않고, 현황은 보내는 시각의 최신 값이다 (S14 PR 판단 기록).</li>
 * <li>응답: 받음 → 보냄, 404·401·403 → 웹훅을 끄고 방장 알림({@link DiscordChannels#markGone}) 뒤 실패, 429 → 디스코드가 준 시간만큼 뒤에 재시도,
 * 5xx·연결 실패 → 발송기 백오프로 재시도, 그 밖의 4xx → 실패(채널에 마지막 오류를 남김).</li>
 * <li>채널이 꺼졌거나(해제·그사이 꺼짐) 404·401·403을 받은 결과 알림은 방장 웹 푸시로 대신 넣는다 ({@link DailyResultFallback}, 사람이 정함
 * 2026-11-28). 현황 게시는 대신 보내지 않는다.</li>
 * </ul>
 */
@Slf4j
public class DiscordSender implements NotificationSender {

	private final StudyChannelRepository channels;

	private final DiscordChannels discordChannels;

	private final DiscordClient client;

	private final StudyProfiles studyProfiles;

	private final StudyDayBoards boards;

	private final PublicLinks links;

	private final DailyResultFallback dailyResultFallback;

	DiscordSender(StudyChannelRepository channels, DiscordChannels discordChannels, DiscordClient client,
			StudyProfiles studyProfiles, StudyDayBoards boards, PublicLinks links, DailyResultFallback dailyResultFallback) {
		this.channels = channels;
		this.discordChannels = discordChannels;
		this.client = client;
		this.studyProfiles = studyProfiles;
		this.boards = boards;
		this.links = links;
		this.dailyResultFallback = dailyResultFallback;
	}

	@Override
	public NotificationChannel channel() {
		return NotificationChannel.DISCORD;
	}

	@Override
	public SendResult send(ClaimedNotification notification, Instant now) {
		Optional<StudyChannel> found = this.channels.findById(notification.studyId()).filter(StudyChannel::isPostable);
		if (found.isEmpty()) {
			this.dailyResultFallback.toOwner(notification);
			return new SendResult.Failed("DISCORD_DISABLED");
		}
		StudyChannel channel = found.get();
		LocalDate studyDay = notification.studyDay();
		if (studyDay == null) {
			return new SendResult.Failed("NO_STUDY_DAY");
		}
		DiscordWebhook webhook;
		try {
			webhook = this.discordChannels.webhookOf(channel);
		}
		catch (IllegalStateException ex) {
			// 암호화 키가 바뀌었는데 옛 키를 넣지 않았거나 값이 깨졌다. 다시 보내도 같다. 키·암호문은 로그에 남기지 않는다.
			log.error("디스코드 웹훅 주소를 풀지 못했어요: study={}, notification={}", notification.studyId(), notification.id());
			return new SendResult.Failed("WEBHOOK_UNREADABLE");
		}
		DiscordMessage message;
		if (NotificationType.DISCORD_STATUS.name().equals(notification.type())) {
			message = status(notification.studyId(), studyDay);
		}
		else if (NotificationType.DAILY_RESULT.name().equals(notification.type())) {
			message = result(notification.studyId(), studyDay);
		}
		else {
			return new SendResult.Failed("UNSUPPORTED_TYPE");
		}
		DiscordPostResult result = this.client.post(webhook, message);
		return switch (result) {
			case DiscordPostResult.Delivered delivered -> new SendResult.Delivered();
			case DiscordPostResult.Gone gone -> {
				this.discordChannels.markGone(notification.studyId(), channel.getDiscordRevision(), gone.reason());
				this.dailyResultFallback.toOwner(notification);
				yield new SendResult.Failed(gone.reason());
			}
			case DiscordPostResult.Retry retry -> new SendResult.Retry(retry.retryAfter(), retry.reason());
			case DiscordPostResult.Rejected rejected -> {
				this.channels.recordError(notification.studyId(), channel.getDiscordRevision(), rejected.reason(), now);
				yield new SendResult.Failed(rejected.reason());
			}
		};
	}

	private DiscordMessage status(long studyId, LocalDate studyDay) {
		StudyProfile profile = this.studyProfiles.of(studyId);
		StudyDayBoard board = this.boards.of(studyId, studyDay);
		return DiscordMessages.status(Cards.status(profile.name(), board, profile.zoneId(), this.links.today(studyId)));
	}

	private DiscordMessage result(long studyId, LocalDate studyDay) {
		StudyProfile profile = this.studyProfiles.of(studyId);
		StudyDayBoard board = this.boards.of(studyId, studyDay);
		return DiscordMessages.result(Cards.result(profile.name(), board, this.links.day(studyId, studyDay)));
	}

}
