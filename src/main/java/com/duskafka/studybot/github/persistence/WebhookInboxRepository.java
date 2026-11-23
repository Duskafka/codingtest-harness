package com.duskafka.studybot.github.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookInboxRepository extends JpaRepository<WebhookInbox, Long> {

	/**
	 * 받은 웹훅을 RECEIVED 상태로 남기고 새 행의 ID를 돌려준다. 같은 {@code (source, delivery_id)}가 이미 있으면 아무것도 하지 않고
	 * 빈 값을 돌려준다 (5.3 '이미 받은 전달', T2).
	 *
	 * <p>PostgreSQL {@code INSERT ... ON CONFLICT DO NOTHING}을 한 문장으로 써서, 같은 전달이 동시에 와도 UNIQUE 위반 없이
	 * 한 요청만 행을 만든다. 나중 요청은 앞 요청이 커밋할 때까지 기다렸다가 빈 값을 받는다. 쓰기 트랜잭션 안에서 불러야 한다.
	 *
	 * @param payloadJson JSON 문서. 호출하는 쪽이 JSON인지 먼저 확인한다 (JSONB로 바꿀 수 없으면 DB 오류가 난다)
	 */
	@Query(value = """
			insert into studybot.webhook_inbox (source, delivery_id, event_type, payload, received_at, status, attempts)
			values (:source, :deliveryId, :eventType, cast(:payload as jsonb), :receivedAt, 'RECEIVED', 0)
			on conflict (source, delivery_id) do nothing
			returning id
			""", nativeQuery = true)
	Optional<Long> insertIfAbsent(@Param("source") String source, @Param("deliveryId") String deliveryId,
			@Param("eventType") String eventType, @Param("payload") String payloadJson,
			@Param("receivedAt") Instant receivedAt);

}
