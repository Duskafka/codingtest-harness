package com.duskafka.studybot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.archfixtures.time.FixtureClockConfiguration;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 현재 시각 직접 호출 금지 규칙이 실제로 걸리는지 예제로 보인다 (S01 만들 것 3번).
 * 예제는 테스트 소스의 {@code com.duskafka.archfixtures.time}에 있다.
 */
class TimeRulesSelfTest {

	private static final String FIXTURES = "com.duskafka.archfixtures.time";

	private static List<String> violations;

	@BeforeAll
	static void evaluateFixtures() {
		EvaluationResult result = TimeRules
			.noDirectCurrentTime(FIXTURES, FixtureClockConfiguration.class.getName())
			.evaluate(new ClassFileImporter().importPackages(FIXTURES));
		violations = result.getFailureReport().getDetails();
	}

	@ParameterizedTest(name = "S01: {0}가 규칙에 걸린다")
	@ValueSource(strings = { "CallsLocalDateNow", "CallsZonedNowWithZone", "ReferencesInstantNow",
			"CallsCurrentTimeMillis", "CreatesDate", "CallsCalendarGetInstance", "CallsClockSystemDefaultZone",
			"CallsChronoDateNow", "CallsChronologyDateNow", "CallsClockTickSeconds", "CallsInstantSourceSystem",
			"CreatesGregorianCalendar", "CallsGregorianGetInstance", "SubclassedClockSystemUtc" })
	@DisplayName("S01: 현재 시각을 직접 읽는 예제가 규칙에 걸린다")
	void directCurrentTimeIsDetected(String fixture) {
		assertThat(violations).anySatisfy(detail -> assertThat(detail).contains(FIXTURES + "." + fixture));
	}

	@Test
	@DisplayName("S01: now(Clock)·dateNow(Clock)·Clock.tick(Clock, ..), System.nanoTime(), 시각을 넘기는 생성자는 통과한다")
	void injectedClockPasses() {
		assertThat(violations).noneSatisfy(detail -> assertThat(detail).contains("UsesInjectedClock"));
	}

	@Test
	@DisplayName("S01: new GregorianCalendar(TimeZone)처럼 시간대만 넘기는 생성자도 걸린다")
	void gregorianCalendarWithZoneIsDetected() {
		assertThat(violations)
			.filteredOn(detail -> detail.contains("CreatesGregorianCalendar"))
			.as("인자 없는 생성자와 TimeZone 생성자 둘 다")
			.hasSize(2);
	}

	@Test
	@DisplayName("S01: Clock 설정 클래스는 시스템 시계를 만들고 now()를 불러도 통과한다")
	void clockConfigurationIsExempt() {
		assertThat(violations).noneSatisfy(detail -> assertThat(detail).contains("FixtureClockConfiguration"));
	}

}
