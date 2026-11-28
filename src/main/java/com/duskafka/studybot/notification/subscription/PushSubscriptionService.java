package com.duskafka.studybot.notification.subscription;

import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.notification.persistence.PushSubscription;
import com.duskafka.studybot.notification.persistence.PushSubscriptionRepository;
import com.duskafka.studybot.notification.webpush.PushEndpointPolicy;
import com.duskafka.studybot.notification.webpush.VapidKeys;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹 푸시 구독 등록·해제 (S13 만들 것 5, api.md 4.4, T28, T19).
 *
 * <ul>
 * <li>구독 주소는 알려진 푸시 서비스만 받는다 ({@link PushEndpointPolicy}, 9.7 SSRF 방지). 키는 형식을 확인한다: {@code p256dh}는 곡선 위의
 * 압축하지 않은 P-256 점(65바이트), {@code auth}는 16바이트 (RFC 8291). 틀리면 400이다.</li>
 * <li>같은 주소를 다시 등록하면 그 행을 지금 사용자·키로 바꾼다 (기기 하나에 구독 하나). 같은 브라우저로 다른 계정에 로그인해 다시 구독하면 지금
 * 계정으로 옮겨진다 (S13 PR 판단 기록).</li>
 * <li>해제는 본인 구독만 된다. 남의 것이나 없는 것은 404다 (T19, 남의 구독이 있는지 알려 주지 않음).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

	static final String ENDPOINT_DETAIL = "알 수 없는 푸시 서비스 주소예요.";

	static final String KEYS_DETAIL = "푸시 구독 키 형식이 맞지 않아요.";

	/** 저장할 User-Agent 길이 (V11 CHECK와 같음). 넘으면 자른다. */
	static final int MAX_USER_AGENT_LENGTH = 512;

	private final PushSubscriptionRepository subscriptions;

	private final PushEndpointPolicy endpointPolicy;

	private final VapidKeys vapidKeys;

	private final Clock clock;

	/** 브라우저 {@code applicationServerKey}로 줄 VAPID 공개키 (base64url). */
	public String vapidPublicKey() {
		return this.vapidKeys.publicKeyBase64Url();
	}

	/** 구독을 등록한다. 같은 주소가 있으면 지금 값으로 바꾼다. */
	@Transactional
	public RegisteredSubscription register(long userId, NewPushSubscription request) {
		if (!this.endpointPolicy.allows(request.endpoint())) {
			throw new BadRequestException(ENDPOINT_DETAIL);
		}
		if (VapidKeys.parsePoint(VapidKeys.decode(request.p256dh())).isEmpty()
				|| VapidKeys.decode(request.auth()).length != 16) {
			throw new BadRequestException(KEYS_DETAIL);
		}
		Instant now = this.clock.instant();
		this.subscriptions.upsert(userId, request.endpoint(), request.p256dh(), request.auth(),
				truncate(request.userAgent()), now);
		PushSubscription saved = this.subscriptions.findByEndpoint(request.endpoint())
			.orElseThrow(() -> new IllegalStateException("넣은 구독이 없어요"));
		return new RegisteredSubscription(saved.getId(), saved.getCreatedAt());
	}

	/**
	 * 내 구독(기기) 한 페이지, ID 순서 (사람이 정함, 2026-11-28 'S13에서 목록 API로'). 구독 주소와 키는 돌려주지 않는다 (기기를 가리키는 비밀
	 * 주소, 9.8 최소 노출). 다른 사용자의 구독은 보이지 않는다 (T19).
	 */
	@Transactional(readOnly = true)
	public Page<SubscriptionSummary> mine(long userId, int page, int size) {
		return this.subscriptions.findByUserIdOrderById(userId, PageRequest.of(page, size))
			.map(row -> new SubscriptionSummary(row.getId(), row.getCreatedAt(), row.getLastSuccessAt(),
					row.getUserAgent()));
	}

	/** 본인 구독을 해제한다. 남의 것이나 없는 것이면 404. */
	@Transactional
	public void delete(long userId, long subscriptionId) {
		if (this.subscriptions.deleteOwned(subscriptionId, userId) == 0) {
			throw new NotFoundException("푸시 구독을 찾을 수 없어요.");
		}
	}

	private static @Nullable String truncate(@Nullable String userAgent) {
		if (userAgent == null || userAgent.isBlank()) {
			return null;
		}
		return userAgent.length() <= MAX_USER_AGENT_LENGTH ? userAgent : userAgent.substring(0, MAX_USER_AGENT_LENGTH);
	}

	/**
	 * 등록할 구독 (브라우저 {@code PushSubscription.toJSON()}에서).
	 *
	 * @param endpoint 구독 주소
	 * @param p256dh 브라우저 공개키 (base64url)
	 * @param auth 인증 비밀 (base64url)
	 * @param userAgent 요청의 User-Agent (기기 구분 표시용, 없어도 됨)
	 */
	public record NewPushSubscription(String endpoint, String p256dh, String auth, @Nullable String userAgent) {

		public NewPushSubscription {
			Objects.requireNonNull(endpoint, "endpoint");
			Objects.requireNonNull(p256dh, "p256dh");
			Objects.requireNonNull(auth, "auth");
		}

	}

	/**
	 * 내 구독 목록의 한 줄.
	 *
	 * @param id 구독 ID (해제할 때 씀)
	 * @param createdAt 처음 등록한 시각
	 * @param lastSuccessAt 마지막으로 이 기기에 보내기에 성공한 시각. 없으면 {@code null}
	 * @param userAgent 등록할 때의 User-Agent (기기 구분 표시용). 없으면 {@code null}
	 */
	public record SubscriptionSummary(long id, Instant createdAt, @Nullable Instant lastSuccessAt,
			@Nullable String userAgent) {
	}

	/**
	 * 등록한 구독.
	 *
	 * @param id 구독 ID (해제할 때 씀)
	 * @param createdAt 처음 등록한 시각
	 */
	public record RegisteredSubscription(long id, Instant createdAt) {
	}

}
