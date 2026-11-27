package com.duskafka.studybot.notification.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹 푸시 구독을 넣고 지우고 발송 결과를 남긴다 (S13 만들 것 5·6). 쓰기는 네이티브 쿼리만 쓰고, 쓰기 메서드는 트랜잭션을 연다(부르는 쪽
 * 트랜잭션이 있으면 거기에 들어간다).
 */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

	/**
	 * 구독을 넣는다. 같은 {@code endpoint}가 이미 있으면 그 행의 사용자·키·User-Agent를 지금 값으로 바꾸고 실패 횟수를 0으로 둔다 (기기
	 * 하나에 구독 하나, endpoint UNIQUE). 같은 브라우저로 다른 계정에 로그인해 다시 구독하면 지금 계정으로 옮겨진다 (S13 PR 판단 기록).
	 */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			insert into studybot.push_subscriptions (user_id, endpoint, p256dh, auth, user_agent, created_at, failure_count)
			values (:userId, :endpoint, :p256dh, :auth, cast(:userAgent as text), :now, 0)
			on conflict (endpoint) do update
			set user_id = excluded.user_id, p256dh = excluded.p256dh, auth = excluded.auth,
				user_agent = excluded.user_agent, failure_count = 0
			""", nativeQuery = true)
	int upsert(@Param("userId") long userId, @Param("endpoint") String endpoint, @Param("p256dh") String p256dh,
			@Param("auth") String auth, @Param("userAgent") @Nullable String userAgent, @Param("now") Instant now);

	Optional<PushSubscription> findByEndpoint(String endpoint);

	/** 사용자의 구독들 (ID 순서). 발송기가 알림 하나를 기기마다 보낸다. */
	List<PushSubscription> findByUserIdOrderById(long userId);

	/**
	 * 본인의 구독을 지운다. 남의 것이나 없는 것은 지우지 않는다 (T19).
	 *
	 * @return 지운 행 수
	 */
	@Transactional
	@Modifying
	@Query(value = "delete from studybot.push_subscriptions where id = :id and user_id = :userId", nativeQuery = true)
	int deleteOwned(@Param("id") long id, @Param("userId") long userId);

	/**
	 * 푸시 서비스가 404·410으로 없앤 구독을 지운다 (5.8, T17). 그새 같은 기기가 다시 구독해 키가 바뀌었으면 지우지 않는다.
	 *
	 * @return 지운 행 수
	 */
	@Transactional
	@Modifying
	@Query(value = "delete from studybot.push_subscriptions where id = :id and p256dh = :p256dh", nativeQuery = true)
	int deleteGone(@Param("id") long id, @Param("p256dh") String p256dh);

	/** 보내기에 성공했다: 마지막 성공 시각을 남기고 실패 횟수를 0으로. */
	@Transactional
	@Modifying
	@Query(value = """
			update studybot.push_subscriptions set last_success_at = :now, failure_count = 0 where id = :id
			""", nativeQuery = true)
	int markSuccess(@Param("id") long id, @Param("now") Instant now);

	/** 보내기에 실패했다(일시 실패 또는 그 밖의 4xx): 실패 횟수를 올린다. */
	@Transactional
	@Modifying
	@Query(value = "update studybot.push_subscriptions set failure_count = failure_count + 1 where id = :id",
			nativeQuery = true)
	int markFailure(@Param("id") long id);

}
