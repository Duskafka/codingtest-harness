package com.duskafka.studybot.notification.discord;

import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.notification.link.PublicLinks;
import com.duskafka.studybot.notification.outbox.NotificationOutbox;
import com.duskafka.studybot.notification.outbox.NotificationOutbox.NewWebPush;
import com.duskafka.studybot.notification.outbox.NotificationType;
import com.duskafka.studybot.notification.outbox.PushMessage;
import com.duskafka.studybot.notification.persistence.StudyChannel;
import com.duskafka.studybot.notification.persistence.StudyChannelRepository;
import com.duskafka.studybot.study.StudyAccess;
import com.duskafka.studybot.study.StudyMode;
import com.duskafka.studybot.study.StudyProfiles;
import com.duskafka.studybot.study.StudyProfiles.StudyProfile;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스터디 디스코드 채널 웹훅의 등록·해제·테스트 게시와 웹훅이 지워졌을 때의 처리 (S14 만들 것 1·2·4, api.md 4.4 {@code PUT·POST
 * /api/studies/{id}/channels/discord…}).
 *
 * <ul>
 * <li>권한: 방장만. 멤버가 아니면 404, 방장이 아니면 403이다 ({@link StudyAccess#requireOwner}). 본문 검사보다 먼저 한다 (T19).</li>
 * <li>혼자 쓰기 스터디는 공유가 없어 409 {@value #SOLO_CODE}다 (R15, 사람이 정함 2026-11-28 "둘 다 거절").</li>
 * <li>주소는 공식 웹훅 주소만 받는다 ({@link DiscordWebhookPolicy}, T27). 정규화한 주소를 암호화해 저장하고 다시 보여 주지 않는다
 * ({@link DiscordWebhookCipher}). 응답은 상태(등록 여부, 켜짐, 마지막 오류)만이다.</li>
 * <li>웹훅이 지워져(404) 끌 때는 방장에게 웹 푸시({@code DISCORD_DISABLED})를 같은 트랜잭션에서 넣는다 (S14 만들 것 4).</li>
 * </ul>
 *
 * <p>지표: {@code studybot.notification.discord.disabled}(404로 끈 웹훅 수).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordChannels {

	static final String SOLO_CODE = "SOLO_STUDY_NO_SHARING";

	static final String SOLO_DETAIL = "혼자 쓰기 스터디는 공유 기능이 없어요.";

	static final String NOT_REGISTERED_CODE = "DISCORD_NOT_REGISTERED";

	static final String GONE_CODE = "DISCORD_WEBHOOK_GONE";

	static final String REJECTED_CODE = "DISCORD_WEBHOOK_REJECTED";

	static final String INVALID_URL_DETAIL = "디스코드 채널 웹훅 주소(https://discord.com/api/webhooks/…)만 등록할 수 있어요.";

	/** 디스코드 꺼짐 알림을 보낼 수 있는 시간 ({@code RULE_CHANGED}와 같음, S14 PR 판단 기록). */
	static final Duration DISABLED_NOTICE_TIME_TO_LIVE = Duration.ofHours(24);

	private final StudyAccess studyAccess;

	private final StudyProfiles studyProfiles;

	private final StudyChannelRepository channels;

	private final DiscordWebhookCipher cipher;

	private final DiscordClient client;

	private final NotificationOutbox outbox;

	private final MeterRegistry meterRegistry;

	private final TransactionTemplate transactions;

	private final Clock clock;

	/**
	 * 웹훅을 등록하거나(주소) 해제한다({@code null}). 방장만. 본문은 권한을 본 뒤에 읽는다 (T19: 남의 스터디에 틀린 본문을 보내도 400이 아니라
	 * 404·403).
	 *
	 * @param webhookUrl 본문에서 읽은 디스코드 채널 웹훅 주소. {@code null}이면 해제. 본문이 틀리면 400을 던진다
	 */
	public ChannelStatus change(long studyId, long userId, Supplier<@Nullable String> webhookUrlOfBody) {
		requireSharingOwner(studyId, userId);
		String webhookUrl = webhookUrlOfBody.get();
		Instant now = this.clock.instant();
		if (webhookUrl == null) {
			this.channels.unregister(studyId, now);
			return status(studyId);
		}
		DiscordWebhook webhook = DiscordWebhookPolicy.parse(webhookUrl)
			.orElseThrow(() -> new BadRequestException(INVALID_URL_DETAIL));
		this.channels.register(studyId, this.cipher.encrypt(studyId, webhook.canonicalUrl()), now);
		return status(studyId);
	}

	/**
	 * 등록한 웹훅으로 테스트 메시지를 한 번 보낸다 (S14 만들 것 2). 방장만. 결과를 바로 알려 준다: 받음 200, 꺼져 있음 409
	 * {@value #NOT_REGISTERED_CODE}, 웹훅이 지워짐(404) 409 {@value #GONE_CODE}(끄고 방장 알림), 그 밖의 4xx 409 {@value #REJECTED_CODE}(마지막
	 * 오류만 남김), 429·5xx·연결 실패 503 {@code DISCORD_UNAVAILABLE}.
	 */
	public ChannelStatus test(long studyId, long userId) {
		StudyProfile profile = requireSharingOwner(studyId, userId);
		StudyChannel channel = this.channels.findById(studyId)
			.filter(StudyChannel::isPostable)
			.orElseThrow(() -> new ConflictException(NOT_REGISTERED_CODE, "등록한 디스코드 웹훅이 없거나 꺼져 있어요."));
		DiscordWebhook webhook = webhookOf(channel);
		DiscordPostResult result = this.client.post(webhook, DiscordMessages.test(profile.name()));
		return switch (result) {
			case DiscordPostResult.Delivered delivered -> status(studyId);
			case DiscordPostResult.Gone gone -> {
				markGone(studyId, channel.getDiscordRevision());
				throw new ConflictException(GONE_CODE, "디스코드 웹훅이 지워져 게시를 껐어요. 웹훅을 다시 만들어 등록해 주세요.");
			}
			case DiscordPostResult.Rejected rejected -> {
				this.channels.recordError(studyId, channel.getDiscordRevision(), rejected.reason(), this.clock.instant());
				throw new ConflictException(REJECTED_CODE, "디스코드가 웹훅 요청을 거절했어요. 웹훅을 다시 만들어 등록해 주세요.");
			}
			case DiscordPostResult.Retry retry -> throw new DiscordUnavailableException();
		};
	}

	/**
	 * 웹훅이 지워져(404) 끄고, 방장에게 {@code DISCORD_DISABLED} 웹 푸시를 넣는다. 리비전이 그대로이고 켜져 있을 때만이다. 그사이 방장이 다시
	 * 등록했거나 해제했으면 아무것도 하지 않는다 (늦게 온 404가 새 웹훅을 끄지 않게).
	 *
	 * <p>끄기와 알림 넣기를 한 트랜잭션으로 묶는다. 테스트 게시({@link #test})처럼 이 빈 안에서도 부르므로 애너테이션 대신
	 * {@link TransactionTemplate}으로 연다(같은 빈 안의 호출은 프록시를 거치지 않는다).
	 *
	 * @param revision 404를 받은 웹훅의 등록 리비전
	 * @return 껐는지
	 */
	public boolean markGone(long studyId, long revision, String reason) {
		return markGone(studyId, revision);
	}

	public boolean markGone(long studyId, long revision) {
		return Boolean.TRUE.equals(this.transactions.execute(status -> disableAndNotify(studyId, revision)));
	}

	private boolean disableAndNotify(long studyId, long revision) {
		Instant now = this.clock.instant();
		if (this.channels.disable(studyId, revision, "HTTP_404", now) == 0) {
			return false;
		}
		StudyProfile profile = this.studyProfiles.of(studyId);
		this.outbox.addWebPush(new NewWebPush(NotificationType.DISCORD_DISABLED, studyId, profile.ownerUserId(), null,
				NotificationOutbox.discordDisabledKey(studyId, revision),
				new PushMessage(NotificationType.DISCORD_DISABLED.name(), profile.name(),
						"디스코드 웹훅이 지워져 게시를 멈췄어요. 스터디 설정에서 다시 등록해 주세요", PublicLinks.settingsPath(studyId)),
				now.plus(DISABLED_NOTICE_TIME_TO_LIVE)));
		this.meterRegistry.counter("studybot.notification.discord.disabled").increment();
		log.info("디스코드 웹훅이 지워져 껐어요: study={}", studyId);
		return true;
	}

	/** 저장한 암호문을 풀어 웹훅 ID·토큰을 돌려준다. 풀 수 없으면(키 문제) {@link IllegalStateException}. */
	DiscordWebhook webhookOf(StudyChannel channel) {
		String ciphertext = channel.getDiscordWebhookUrlEnc();
		if (ciphertext == null) {
			throw new IllegalStateException("디스코드 웹훅 주소가 없어요");
		}
		return DiscordWebhookPolicy.parse(this.cipher.decrypt(channel.getStudyId(), ciphertext))
			.orElseThrow(() -> new IllegalStateException("저장된 디스코드 웹훅 주소가 공식 주소가 아니에요"));
	}

	/** 지금 상태. 행이 없으면 등록 안 됨. */
	public ChannelStatus status(long studyId) {
		Optional<StudyChannel> channel = this.channels.findById(studyId);
		return channel.map(row -> new ChannelStatus(row.getDiscordWebhookUrlEnc() != null, row.isDiscordEnabled(),
				row.getDiscordLastError()))
			.orElse(new ChannelStatus(false, false, null));
	}

	private StudyProfile requireSharingOwner(long studyId, long userId) {
		this.studyAccess.requireOwner(studyId, userId);
		StudyProfile profile = this.studyProfiles.of(studyId);
		if (profile.mode() == StudyMode.SOLO) {
			throw new ConflictException(SOLO_CODE, SOLO_DETAIL);
		}
		return profile;
	}

	/**
	 * 디스코드 채널 설정 상태. 웹훅 주소는 담지 않는다.
	 *
	 * @param registered 웹훅 주소가 저장돼 있는지
	 * @param enabled 게시하는지 (해제했거나 웹훅이 지워졌으면 거짓)
	 * @param lastError 마지막 실패의 까닭 (예: {@code HTTP_404}). 없으면 {@code null}
	 */
	public record ChannelStatus(boolean registered, boolean enabled, @Nullable String lastError) {

	}

}
