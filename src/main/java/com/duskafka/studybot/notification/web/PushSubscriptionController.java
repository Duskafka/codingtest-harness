package com.duskafka.studybot.notification.web;

import com.duskafka.studybot.auth.LoginUser;
import com.duskafka.studybot.notification.subscription.PushSubscriptionService;
import com.duskafka.studybot.notification.subscription.PushSubscriptionService.NewPushSubscription;
import com.duskafka.studybot.notification.subscription.PushSubscriptionService.RegisteredSubscription;
import com.duskafka.studybot.notification.webpush.PushEndpointPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웹 푸시 공개키와 구독 API (api.md 4.4, S13 만들 것 5). 권한: 공개키는 공개, 등록은 로그인, 해제는 본인. 응답 모양은 프론트(S16)가 쓰는
 * 계약이라 S13 PR 판단 기록에 '꼭 봐 주세요'로 남긴다.
 */
@RestController
@RequiredArgsConstructor
class PushSubscriptionController {

	private final PushSubscriptionService service;

	/** 브라우저가 {@code pushManager.subscribe({applicationServerKey})}에 넣을 VAPID 공개키. 로그인 없이 읽는다. */
	@GetMapping("/api/push/vapid-public-key")
	VapidPublicKeyResponse vapidPublicKey() {
		return new VapidPublicKeyResponse(this.service.vapidPublicKey());
	}

	/** 구독을 등록한다. 같은 주소를 다시 등록해도 201과 같은 ID다. */
	@PostMapping("/api/me/push-subscriptions")
	@ResponseStatus(HttpStatus.CREATED)
	SubscriptionResponse register(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody SubscriptionRequest request,
			@RequestHeader(name = HttpHeaders.USER_AGENT, required = false) @Nullable String userAgent) {
		RegisteredSubscription saved = this.service.register(loginUser.userId(),
				new NewPushSubscription(request.endpoint(), request.keys().p256dh(), request.keys().auth(), userAgent));
		return new SubscriptionResponse(saved.id(), saved.createdAt());
	}

	/** 구독을 해제한다. 남의 것이나 없는 것은 404. */
	@DeleteMapping("/api/me/push-subscriptions/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable long id) {
		this.service.delete(loginUser.userId(), id);
	}

	/** @param publicKey 압축하지 않은 P-256 공개키(65바이트)의 base64url */
	record VapidPublicKeyResponse(String publicKey) {
	}

	/**
	 * 브라우저 {@code PushSubscription.toJSON()} 그대로. {@code expirationTime}은 받기만 하고 쓰지 않는다.
	 *
	 * @param endpoint 구독 주소 (알려진 푸시 서비스만, T28)
	 * @param expirationTime 브라우저가 주는 만료 시각(밀리초). 쓰지 않는다
	 * @param keys 암호화 키
	 */
	record SubscriptionRequest(@NotBlank @Size(max = PushEndpointPolicy.MAX_LENGTH) String endpoint,
			@Nullable Long expirationTime, @NotNull @Valid Keys keys) {

		/**
		 * @param p256dh 브라우저 공개키 (base64url)
		 * @param auth 인증 비밀 (base64url)
		 */
		record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
		}

	}

	/**
	 * @param id 구독 ID (해제할 때 씀)
	 * @param createdAt 처음 등록한 시각
	 */
	record SubscriptionResponse(long id, Instant createdAt) {
	}

}
