package com.duskafka.studybot.notification.discord;

import com.duskafka.studybot.notification.persistence.StudyChannel;
import com.duskafka.studybot.notification.persistence.StudyChannelRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 키를 교체한 뒤 저장된 디스코드 웹훅 주소를 지금 키로 다시 암호화한다 (infra.md 9.6 '키 교체 후 저장된 URL 재암호화').
 *
 * <p>교체 절차 (S14 PR 판단 기록): ① 새 키를 만들어 {@code DISCORD_WEBHOOK_ENCRYPTION_KEY}에, 옛 키를
 * {@code DISCORD_WEBHOOK_ENCRYPTION_KEY_PREVIOUS}에 넣고 배포한다 → ② 앱이 뜨면 여기서 옛 키로 저장된 행을 모두 다시 암호화한다(로그와 지표로
 * 확인) → ③ 남은 행이 0이면 옛 키를 빼고 다시 배포한다. 그동안 옛 키로 저장된 주소도 풀리므로 게시는 멈추지 않는다.
 *
 * <p>행마다 '읽은 암호문과 같을 때만' 바꾼다. 그사이 방장이 다시 등록했으면 건드리지 않는다. 다시 돌려도 바뀌는 것이 없다. 서버 두 대가 겹쳐도
 * 같은 행을 두 번 바꾸지 않는다. 풀 수 없는 행(모르는 키)은 오류 로그만 남기고 기동은 막지 않는다 (게시할 때 실패로 남음).
 *
 * <p>지표: {@code studybot.notification.discord.rewrapped}(다시 암호화한 행 수), {@code studybot.notification.discord.unreadable}(풀 수
 * 없는 행 수).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordKeyRotation {

	private final StudyChannelRepository channels;

	private final DiscordWebhookCipher cipher;

	private final MeterRegistry meterRegistry;

	@EventListener(ApplicationReadyEvent.class)
	void onReady() {
		rewrap();
	}

	/**
	 * 지금 키가 아닌 키로 저장된 주소를 지금 키로 다시 암호화한다.
	 *
	 * @return 다시 암호화한 행 수
	 */
	public int rewrap() {
		int rewrapped = 0;
		int unreadable = 0;
		for (StudyChannel channel : this.channels.findNotEncryptedWith(this.cipher.currentPrefix())) {
			String old = channel.getDiscordWebhookUrlEnc();
			if (old == null) {
				continue;
			}
			String replacement;
			try {
				replacement = this.cipher.encrypt(channel.getStudyId(), this.cipher.decrypt(channel.getStudyId(), old));
			}
			catch (IllegalStateException ex) {
				unreadable++;
				log.error("디스코드 웹훅 주소를 풀지 못해 다시 암호화하지 못했어요: study={}", channel.getStudyId());
				continue;
			}
			rewrapped += this.channels.replaceCiphertext(channel.getStudyId(), old, replacement);
		}
		if (rewrapped > 0 || unreadable > 0) {
			log.info("디스코드 웹훅 주소 다시 암호화: {}개, 풀 수 없음: {}개 (지금 키 {})", rewrapped, unreadable,
					this.cipher.currentKeyId());
		}
		this.meterRegistry.counter("studybot.notification.discord.rewrapped").increment(rewrapped);
		this.meterRegistry.counter("studybot.notification.discord.unreadable").increment(unreadable);
		return rewrapped;
	}

}
