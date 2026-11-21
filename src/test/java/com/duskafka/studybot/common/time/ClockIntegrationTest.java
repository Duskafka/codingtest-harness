package com.duskafka.studybot.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * 통합 테스트에서 현재 시각을 정하고 옮기는 예제 (S01 만들 것 4번).
 * 이후 판정·알림 테스트도 이 방식으로 시각을 다룬다.
 */
@IntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClockIntegrationTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	/** 앱의 빈들이 주입받는 Clock. 테스트에서는 MutableClock이 들어온다. */
	@Autowired
	private Clock clock;

	@Autowired
	private MutableClock testClock;

	@Autowired
	private ApplicationContext context;

	@Test
	@Order(1)
	@DisplayName("S01: 시각을 정하고 옮기면 주입된 Clock이 그 시각을 준다 (서울 자정을 넘기는 예)")
	void setAndAdvanceTime() {
		testClock.setInstant(Instant.parse("2026-03-01T14:59:00Z")); // 서울 2026-03-01 23:59

		assertThat(clock.instant()).isEqualTo(Instant.parse("2026-03-01T14:59:00Z"));
		assertThat(LocalDate.now(clock.withZone(SEOUL))).isEqualTo(LocalDate.of(2026, 3, 1));

		testClock.advance(Duration.ofMinutes(2)); // 서울 2026-03-02 00:01

		assertThat(clock.instant()).isEqualTo(Instant.parse("2026-03-01T15:01:00Z"));
		assertThat(LocalDate.now(clock.withZone(SEOUL))).isEqualTo(LocalDate.of(2026, 3, 2));
	}

	@Test
	@Order(2)
	@DisplayName("S01: 앞 테스트가 옮긴 시각은 다음 테스트로 새지 않는다 (기본 시각으로 돌아감)")
	void clockIsResetBetweenTests() {
		assertThat(clock.instant()).isEqualTo(MutableClock.DEFAULT_INSTANT);
	}

	@Test
	@DisplayName("S01: 앱의 기본 Clock 빈(clock)은 UTC 시스템 시계이고, 테스트에서는 테스트용 시계가 대신 주입된다")
	void applicationClockIsUtcSystemClock() {
		Clock applicationClock = context.getBean("clock", Clock.class);

		assertThat(applicationClock).isNotInstanceOf(MutableClock.class);
		assertThat(applicationClock.getZone()).isEqualTo(ZoneOffset.UTC);
		assertThat(clock).isSameAs(testClock);
	}

}
