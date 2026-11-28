package com.duskafka.studybot.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * 스터디의 디스코드 채널 설정 한 행 (data-model.md 4.3 {@code study_channels}, S14). 행은 {@link StudyChannelRepository}의 네이티브
 * 쿼리로 넣고 바꾼다. 이 엔티티는 스키마 확인({@code ddl-auto=validate})과 읽기용이다.
 *
 * <p>{@code discordWebhookUrlEnc}는 앱 수준에서 암호화한 웹훅 URL이다 ({@code DiscordWebhookCipher}). 평문 URL은 로그·응답에 내보내지 않는다
 * (2.7 '웹훅 URL이 새면 누구나 그 채널에 글을 올릴 수 있다').
 */
@Entity
@Table(name = "study_channels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyChannel {

	@Id
	@Column(name = "study_id")
	private Long studyId;

	/** 암호화한 웹훅 URL. 해제했으면 비어 있다. */
	@Column(name = "discord_webhook_url_enc")
	private @Nullable String discordWebhookUrlEnc;

	/** 디스코드로 게시하는지. 해제했거나 웹훅이 지워졌으면(404) 거짓이다. */
	@Column(name = "discord_enabled", nullable = false)
	private boolean discordEnabled;

	/** 마지막 실패의 까닭 (예: {@code HTTP_404}). 응답 본문은 남기지 않는다. */
	@Column(name = "discord_last_error")
	private @Nullable String discordLastError;

	/** 등록·해제할 때마다 1씩 오른다. 늦게 온 404가 새로 등록한 웹훅을 끄지 않게 한다. */
	@Column(name = "discord_revision", nullable = false)
	private long discordRevision;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** 지금 게시할 수 있는지: 켜져 있고 주소가 있다. */
	public boolean isPostable() {
		return this.discordEnabled && this.discordWebhookUrlEnc != null;
	}

}
