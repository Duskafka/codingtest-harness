package com.duskafka.studybot.notification.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송기가 보낼 알림을 가져간다 (5.8 '발송 대기 행 (SKIP LOCKED)', pseudocode '발송'). 가져가기는 짧은 트랜잭션 하나로 끝내고 먼저 커밋한다.
 * 외부 발송은 그 뒤 트랜잭션 밖에서 한다.
 *
 * <p>읽기는 {@link JdbcClient}로 한다. 네이티브 쿼리의 인터페이스 프로젝션으로 {@code timestamptz}·{@code date}를 {@code Instant}·
 * {@code LocalDate}로 받는 변환에 기대지 않으려는 것이다. JPA 트랜잭션 관리자가 같은 JDBC 연결을 내주므로 같은 트랜잭션이다.
 */
@Component
@RequiredArgsConstructor
public class NotificationQueue {

	private final JdbcClient jdbc;

	/**
	 * 보낼 시각이 된 발송 대기 행을 가장 오래된 것부터 {@code limit}개까지 잠그고 발송 중으로 바꾼다. 다른 발송기가 잠근 행은 건너뛴다
	 * ({@code FOR UPDATE SKIP LOCKED}). 새 트랜잭션에서 하고 돌아오기 전에 커밋한다 (5.8 '먼저 커밋').
	 *
	 * @param lockedUntil 잠금 만료 시각. 이 발송기의 소유 표시라 DB 정밀도(마이크로초)로 잘라서 넘긴다
	 * @return 가져간 행 (ID 순서)
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<ClaimedNotification> claim(Instant now, Instant lockedUntil, int limit) {
		return this.jdbc.sql("""
				with due as (
					select id from studybot.notifications
					where status = 'PENDING' and next_attempt_at <= :now
					order by id
					limit :limit
					for update skip locked
				)
				update studybot.notifications n set status = 'SENDING', locked_until = :lockedUntil
				from due where n.id = due.id
				returning n.id, n.type, n.channel, n.study_id, n.user_id, n.study_day, cast(n.payload as text) as payload,
					n.attempts, n.expires_at
				""")
			.param("now", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
			.param("lockedUntil", OffsetDateTime.ofInstant(lockedUntil, java.time.ZoneOffset.UTC))
			.param("limit", limit)
			.query((rs, rowNum) -> new ClaimedNotification(rs.getLong("id"), rs.getString("type"), rs.getString("channel"),
					rs.getLong("study_id"), rs.getObject("user_id", Long.class), rs.getObject("study_day", LocalDate.class),
					rs.getString("payload"), rs.getInt("attempts"),
					rs.getObject("expires_at", OffsetDateTime.class).toInstant(), lockedUntil))
			.list()
			.stream()
			.sorted(java.util.Comparator.comparingLong(ClaimedNotification::id))
			.toList();
	}

	/**
	 * 발송기가 가져간 알림 한 행.
	 *
	 * @param type 알림 종류 이름
	 * @param channel 채널 이름
	 * @param payload 보낼 JSON
	 * @param attempts 지금까지 시도한 횟수
	 * @param expiresAt 만료 시각
	 * @param token 가져갈 때 정한 잠금 만료 시각. 결과를 남길 때 조건으로 쓴다
	 */
	public record ClaimedNotification(long id, String type, String channel, long studyId, @Nullable Long userId,
			@Nullable LocalDate studyDay, String payload, int attempts, Instant expiresAt, Instant token) {

		public ClaimedNotification {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(channel, "channel");
			Objects.requireNonNull(payload, "payload");
			Objects.requireNonNull(expiresAt, "expiresAt");
			Objects.requireNonNull(token, "token");
		}

	}

}
