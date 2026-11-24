package com.duskafka.studybot.github.webhook;

import com.duskafka.studybot.github.persistence.WebhookInbox;
import com.duskafka.studybot.github.persistence.WebhookInboxRepository;
import com.duskafka.studybot.github.persistence.WebhookSource;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 서명을 확인한 웹훅을 수신함에 남기고 같은 요청 안에서 처리한다 (5.3 P1, D21). 트랜잭션은 셋으로 나눈다.
 *
 * <ol>
 * <li><b>저장:</b> 수신함 INSERT만 하는 트랜잭션을 먼저 커밋한다. 이미 있는 전달이면 아무것도 하지 않는다 (T2).
 * 저장하지 못하면 503이다.</li>
 * <li><b>처리:</b> 새 트랜잭션에서 처리기를 부르고, 같은 트랜잭션에서 수신함을 PROCESSED·IGNORED로 바꾼다.
 * 처리 결과와 상태가 함께 커밋되거나 함께 롤백된다 (P2 워커 그림의 "처리 결과 반영과 상태 PROCESSED를 한 트랜잭션으로"와 같다).</li>
 * <li><b>실패 기록:</b> 처리 트랜잭션이 예외로 끝나면(커밋 실패 포함) 롤백된 뒤 또 새 트랜잭션에서 FAILED와 오류 종류를 남긴다
 * ({@link WebhookFailureDescription}, 메시지는 로그에만). 이것도 실패하면 로그만 남긴다. 행은 RECEIVED로 남는다.</li>
 * </ol>
 *
 * 처리가 어떻게 끝나든 받기는 성공이다 (응답 202). 트랜잭션 경계를 코드에서 보이게 하려고 {@link TransactionTemplate}을 쓴다.
 */
@Slf4j
@Component
public class GithubWebhookReceiver {

	/** 받은 결과. */
	enum Receipt {

		/** 새 전달이라 남기고 처리했다 (처리 결과는 수신함 상태에 있다). */
		ACCEPTED,

		/** 이미 받은 전달이라 아무것도 하지 않았다. */
		DUPLICATE

	}

	private final WebhookInboxRepository inboxRepository;

	private final GithubEventHandlers handlers;

	private final Clock clock;

	private final TransactionTemplate newTransaction;

	GithubWebhookReceiver(WebhookInboxRepository inboxRepository, GithubEventHandlers handlers, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.inboxRepository = inboxRepository;
		this.handlers = handlers;
		this.clock = clock;
		this.newTransaction = new TransactionTemplate(transactionManager);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * @param payload 본문 JSON (처리기에 넘긴다)
	 * @param payloadJson 본문 원문 (수신함에 남긴다)
	 * @throws WebhookStorageUnavailableException 수신함에 저장하지 못함 (DB 장애)
	 */
	Receipt receive(GithubEvent event, String deliveryId, JsonNode payload, String payloadJson) {
		Instant receivedAt = clock.instant();
		Long inboxId = store(event, deliveryId, payloadJson, receivedAt);
		if (inboxId == null) {
			log.info("이미 받은 웹훅이라 처리하지 않아요: delivery={}, event={}", deliveryId, event.headerValue());
			return Receipt.DUPLICATE;
		}
		process(new GithubWebhookEvent(inboxId, deliveryId, event, payload, receivedAt));
		return Receipt.ACCEPTED;
	}

	private @Nullable Long store(GithubEvent event, String deliveryId, String payloadJson, Instant receivedAt) {
		try {
			return newTransaction.execute(status -> inboxRepository
				.insertIfAbsent(WebhookSource.GITHUB.name(), deliveryId, event.headerValue(), payloadJson, receivedAt)
				.orElse(null));
		}
		catch (DataAccessException | TransactionException ex) {
			log.error("웹훅을 수신함에 저장하지 못했어요: delivery={}, event={}", deliveryId, event.headerValue(), ex);
			throw new WebhookStorageUnavailableException(ex);
		}
	}

	private void process(GithubWebhookEvent event) {
		try {
			newTransaction.executeWithoutResult(status -> {
				HandlingResult result = handle(event);
				WebhookInbox inbox = loadInbox(event);
				Instant processedAt = clock.instant();
				switch (result) {
					case PROCESSED -> inbox.markProcessed(processedAt);
					case IGNORED -> inbox.markIgnored(processedAt);
				}
			});
		}
		catch (RuntimeException ex) {
			log.error("웹훅 처리에 실패했어요: delivery={}, event={}, inbox={}", event.deliveryId(), event.event().headerValue(),
					event.inboxId(), ex);
			recordFailure(event, ex);
		}
	}

	private HandlingResult handle(GithubWebhookEvent event) {
		// 받는 이벤트는 모두 처리기가 있어야 앱이 뜬다 (GithubEventHandlers#requireAllEvents, S08 만들 것 7).
		GithubEventHandler handler = handlers.find(event.event())
			.orElseThrow(() -> new IllegalStateException("처리기가 없는 이벤트예요: " + event.event().headerValue()));
		HandlingResult result = handler.handle(event);
		if (result == null) {
			throw new IllegalStateException("처리기가 결과를 돌려주지 않았어요: " + handler.getClass().getName());
		}
		return result;
	}

	private void recordFailure(GithubWebhookEvent event, RuntimeException cause) {
		try {
			newTransaction.executeWithoutResult(status -> loadInbox(event).markFailed(WebhookFailureDescription.of(cause)));
		}
		catch (RuntimeException ex) {
			log.error("웹훅 처리 실패를 수신함에 남기지 못했어요. 행은 RECEIVED로 남아요: delivery={}, inbox={}",
					event.deliveryId(), event.inboxId(), ex);
		}
	}

	private WebhookInbox loadInbox(GithubWebhookEvent event) {
		return inboxRepository.findById(event.inboxId())
			.orElseThrow(() -> new IllegalStateException("수신함 행이 없어요: " + event.inboxId()));
	}

}
