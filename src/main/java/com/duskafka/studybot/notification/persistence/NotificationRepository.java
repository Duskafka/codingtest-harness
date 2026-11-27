package com.duskafka.studybot.notification.persistence;

import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 outbox 행을 넣고 상태를 바꾼다 (5.8, S13 만들 것 2·3). 발송기가 행을 가져가는 쿼리는 {@link NotificationQueue}에 있다.
 * 쓰기 메서드는 트랜잭션을 연다(부르는 쪽 트랜잭션이 있으면 거기에 들어간다). 발송기는 외부 발송을 트랜잭션 밖에서 하고 결과만 이렇게 남긴다.
 *
 * <p>쓰기는 엔티티를 거치지 않고 네이티브 쿼리만 쓴다 ({@code MemberDayRepository} 머리말과 같은 이유). payload(JSONB)는 문자열로 넣는다.
 * 발송 결과를 남기는 쿼리는 모두 {@code status = 'SENDING' and locked_until = :token} 조건을 건다. 잠금이 만료돼 다른 발송기가 다시
 * 가져간 행을, 늦게 끝난 발송기가 덮어쓰지 않게 하려는 것이다.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/**
	 * 알림을 넣는다. 같은 {@code dedupe_key}가 있으면 아무것도 하지 않는다 (5.8 '중복 생성 금지', T15). 처음 시도 시각은 만든 시각이다.
	 *
	 * @return 넣은 행 수
	 */
	@Transactional
	@Modifying
	@Query(value = """
			insert into studybot.notifications
				(type, channel, study_id, user_id, study_day, dedupe_key, payload, status, attempts, next_attempt_at,
				expires_at, created_at)
			values (:type, :channel, :studyId, cast(:userId as bigint), cast(:studyDay as date), :dedupeKey,
				cast(:payload as jsonb), 'PENDING', 0, :now, :expiresAt, :now)
			on conflict (dedupe_key) do nothing
			""", nativeQuery = true)
	int insertIfAbsent(@Param("type") String type, @Param("channel") String channel, @Param("studyId") long studyId,
			@Param("userId") @Nullable Long userId, @Param("studyDay") @Nullable LocalDate studyDay,
			@Param("dedupeKey") String dedupeKey, @Param("payload") String payload, @Param("expiresAt") Instant expiresAt,
			@Param("now") Instant now);

	/** 잠금이 만료된 발송 중 행을 발송 대기로 되돌린다 (5.8 '별도 정리', 상태도 'SENDING → PENDING: 잠금 만료'). 시도 횟수는 그대로다. */
	@Transactional
	@Modifying
	@Query(value = """
			update studybot.notifications set status = 'PENDING', locked_until = null
			where status = 'SENDING' and locked_until < :now
			""", nativeQuery = true)
	int releaseExpiredLocks(@Param("now") Instant now);

	/**
	 * 발송 중 행을 끝낸다: 보냄(SENT), 실패(FAILED), 건너뜀(SKIPPED), 만료(EXPIRED).
	 *
	 * @param token 가져갈 때 정한 잠금 만료 시각
	 * @param attempted 보내 봤으면 1(시도 횟수를 올림), 보내지 않았으면 0
	 * @param lastError 실패 까닭. 실패가 아니면 {@code null}
	 * @return 바꾼 행 수. 0이면 잠금이 만료돼 다른 발송기가 가져간 것이다
	 */
	@Transactional
	@Modifying
	@Query(value = """
			update studybot.notifications
			set status = :status, locked_until = null, attempts = attempts + :attempted,
				sent_at = case when :status = 'SENT' then cast(:now as timestamptz) else null end,
				last_error = cast(:lastError as text)
			where id = :id and status = 'SENDING' and locked_until = :token
			""", nativeQuery = true)
	int finish(@Param("id") long id, @Param("token") Instant token, @Param("status") String status,
			@Param("attempted") int attempted, @Param("lastError") @Nullable String lastError, @Param("now") Instant now);

	/**
	 * 일시 실패한 발송 중 행을 발송 대기로 되돌리고 다음 시도 시각을 정한다 (429·5xx, 5.8 'SENDING → PENDING: 일시 실패'). 시도 횟수를 올린다.
	 *
	 * @return 바꾼 행 수
	 */
	@Transactional
	@Modifying
	@Query(value = """
			update studybot.notifications
			set status = 'PENDING', locked_until = null, attempts = attempts + 1, next_attempt_at = :nextAttemptAt,
				last_error = :lastError
			where id = :id and status = 'SENDING' and locked_until = :token
			""", nativeQuery = true)
	int retryLater(@Param("id") long id, @Param("token") Instant token, @Param("nextAttemptAt") Instant nextAttemptAt,
			@Param("lastError") String lastError);

}
