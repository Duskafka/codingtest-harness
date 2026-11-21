package com.duskafka.studybot;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * 테스트 메서드마다 {@link MutableClock}을 기본 시각으로 되돌린다.
 * 통합 테스트는 컨텍스트(그리고 시계 빈)를 공유하므로, 앞 테스트가 옮긴 시각이 다음 테스트로 새지 않게 한다.
 * JUnit의 {@code @BeforeEach}보다 먼저 돌아서, 테스트는 {@code @BeforeEach}에서 시각을 정하면 된다.
 */
public class MutableClockResetListener extends AbstractTestExecutionListener {

	@Override
	public void beforeTestMethod(TestContext testContext) {
		testContext.getApplicationContext().getBeanProvider(MutableClock.class).ifAvailable(MutableClock::reset);
	}

	@Override
	public int getOrder() {
		// 의존성 주입 같은 기본 리스너 뒤에 돈다.
		return LOWEST_PRECEDENCE;
	}

}
