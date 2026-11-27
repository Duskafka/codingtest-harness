package com.duskafka.studybot.notification;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * S13 테스트가 알림·푸시 구독 행을 준비하고 읽는 도우미. 사용자·스터디·규칙·멤버십·멤버 일은
 * {@link com.duskafka.studybot.progress.StudyDayRows}로 넣는다.
 */
public final class NotificationRows {

	private static final String SELECT = """
			select id, type, channel, study_id, user_id, study_day, dedupe_key, cast(payload as text) as payload, status,
				attempts, next_attempt_at, locked_until, sent_at, expires_at, last_error, created_at
			from studybot.notifications
			""";

	private final JdbcClient jdbc;

	public NotificationRows(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** 이 스터디의 알림들 (ID 순서). */
	public List<NotificationRow> ofStudy(long studyId) {
		return jdbc.sql(SELECT + " where study_id = :studyId order by id")
			.param("studyId", studyId)
			.query((rs, rowNum) -> row(rs))
			.list();
	}

	/** 이 스터디의 이 종류 알림들 (ID 순서). */
	public List<NotificationRow> ofStudy(long studyId, String type) {
		return ofStudy(studyId).stream().filter(row -> row.type().equals(type)).toList();
	}

	public NotificationRow byId(long id) {
		return jdbc.sql(SELECT + " where id = :id").param("id", id).query((rs, rowNum) -> row(rs)).single();
	}

	/**
	 * 알림을 바로 넣는다 (생성기를 거치지 않음). 웹 푸시, 발송 대기, 처음 시도 시각은 {@code nextAttemptAt}.
	 *
	 * @return 알림 ID
	 */
	public long insertPending(String type, long studyId, long userId, @Nullable LocalDate studyDay, String payload,
			Instant nextAttemptAt, Instant expiresAt) {
		return jdbc.sql("""
				insert into studybot.notifications
					(type, channel, study_id, user_id, study_day, dedupe_key, payload, status, attempts, next_attempt_at,
					expires_at, created_at)
				values (:type, 'WEB_PUSH', :studyId, :userId, :studyDay, :dedupeKey, cast(:payload as jsonb), 'PENDING', 0,
					:nextAttemptAt, :expiresAt, :nextAttemptAt)
				returning id
				""")
			.param("type", type)
			.param("studyId", studyId)
			.param("userId", userId)
			.param("studyDay", studyDay)
			.param("dedupeKey", "TEST:" + type + ":" + studyId + ":" + userId + ":" + UUID.randomUUID())
			.param("payload", payload)
			.param("nextAttemptAt", Timestamp.from(nextAttemptAt))
			.param("expiresAt", Timestamp.from(expiresAt))
			.query(Long.class)
			.single();
	}

	/** 발송 중으로 남은 행을 흉내 낸다 (발송 중에 서버가 죽음). */
	public void markSending(long id, Instant lockedUntil) {
		jdbc.sql("update studybot.notifications set status = 'SENDING', locked_until = :lockedUntil where id = :id")
			.param("lockedUntil", Timestamp.from(lockedUntil))
			.param("id", id)
			.update();
	}

	/**
	 * 다른 테스트가 남긴 발송 대기·발송 중 알림을 만료로 바꾼다. 발송기는 DB 전체에서 보낼 행을 고르므로, 발송기 테스트가 자기 행만 보게 한다.
	 */
	public void expireLeftovers() {
		jdbc.sql("""
				update studybot.notifications set status = 'EXPIRED', locked_until = null
				where status in ('PENDING', 'SENDING')
				""").update();
	}

	/** 푸시 구독을 바로 넣는다 (등록 API를 거치지 않음). */
	public long insertSubscription(long userId, String endpoint, PushReceiver receiver) {
		return jdbc.sql("""
				insert into studybot.push_subscriptions (user_id, endpoint, p256dh, auth, created_at)
				values (:userId, :endpoint, :p256dh, :auth, :createdAt) returning id
				""")
			.param("userId", userId)
			.param("endpoint", endpoint)
			.param("p256dh", receiver.p256dh())
			.param("auth", receiver.auth())
			.param("createdAt", Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")))
			.query(Long.class)
			.single();
	}

	public Optional<SubscriptionRow> subscription(long id) {
		return jdbc.sql("""
				select id, user_id, endpoint, p256dh, auth, user_agent, last_success_at, failure_count
				from studybot.push_subscriptions where id = :id
				""")
			.param("id", id)
			.query((rs, rowNum) -> subscriptionRow(rs))
			.optional();
	}

	public List<SubscriptionRow> subscriptionsByEndpoint(String endpoint) {
		return jdbc.sql("""
				select id, user_id, endpoint, p256dh, auth, user_agent, last_success_at, failure_count
				from studybot.push_subscriptions where endpoint = :endpoint
				""")
			.param("endpoint", endpoint)
			.query((rs, rowNum) -> subscriptionRow(rs))
			.list();
	}

	/** 알림 생성의 진행 지점 ({@code studies.last_reminded_day}). */
	public @Nullable LocalDate lastRemindedDay(long studyId) {
		return jdbc.sql("select last_reminded_day from studybot.studies where id = :id")
			.param("id", studyId)
			.query((rs, rowNum) -> rs.getObject("last_reminded_day", LocalDate.class))
			.list()
			.getFirst();
	}

	public void setLastRemindedDay(long studyId, @Nullable LocalDate day) {
		jdbc.sql("update studybot.studies set last_reminded_day = :day where id = :id")
			.param("day", day)
			.param("id", studyId)
			.update();
	}

	/** 규칙 버전의 알림 오프셋을 바꾼다. */
	public void setReminderOffset(long ruleId, int minutes) {
		jdbc.sql("update studybot.study_rules set reminder_offset_minutes = :minutes where id = :id")
			.param("minutes", minutes)
			.param("id", ruleId)
			.update();
	}

	private static NotificationRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new NotificationRow(rs.getLong("id"), rs.getString("type"), rs.getString("channel"),
				rs.getLong("study_id"), rs.getObject("user_id", Long.class), rs.getObject("study_day", LocalDate.class),
				rs.getString("dedupe_key"), rs.getString("payload"), rs.getString("status"), rs.getInt("attempts"),
				toInstant(rs.getObject("next_attempt_at", OffsetDateTime.class)),
				toInstant(rs.getObject("locked_until", OffsetDateTime.class)),
				toInstant(rs.getObject("sent_at", OffsetDateTime.class)),
				toInstant(rs.getObject("expires_at", OffsetDateTime.class)), rs.getString("last_error"),
				toInstant(rs.getObject("created_at", OffsetDateTime.class)));
	}

	private static SubscriptionRow subscriptionRow(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new SubscriptionRow(rs.getLong("id"), rs.getLong("user_id"), rs.getString("endpoint"),
				rs.getString("p256dh"), rs.getString("auth"), rs.getString("user_agent"),
				toInstant(rs.getObject("last_success_at", OffsetDateTime.class)), rs.getInt("failure_count"));
	}

	private static @Nullable Instant toInstant(@Nullable OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}

	public record NotificationRow(long id, String type, String channel, long studyId, @Nullable Long userId,
			@Nullable LocalDate studyDay, String dedupeKey, String payload, String status, int attempts,
			@Nullable Instant nextAttemptAt, @Nullable Instant lockedUntil, @Nullable Instant sentAt,
			@Nullable Instant expiresAt, @Nullable String lastError, @Nullable Instant createdAt) {

	}

	public record SubscriptionRow(long id, long userId, String endpoint, String p256dh, String auth,
			@Nullable String userAgent, @Nullable Instant lastSuccessAt, int failureCount) {

	}

}
