package com.duskafka.studybot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;
import java.time.InstantSource;
import java.time.chrono.Chronology;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 * 시간 규칙: 시간은 주입된 {@link java.time.Clock}으로만 다룬다 (CLAUDE.md, S01 만들 것 3번).
 *
 * <p>금지하는 것 (호출과 메서드 참조 모두):
 * <ul>
 * <li>java.time·java.time.chrono 날짜/시각 타입의 {@code now()}, {@code now(ZoneId)}.
 * {@code now(Clock)}은 허용</li>
 * <li>java.time.chrono {@code Chronology}의 {@code dateNow()}, {@code dateNow(ZoneId)}.
 * {@code dateNow(Clock)}은 허용</li>
 * <li>{@code Clock.systemUTC()}, {@code Clock.systemDefaultZone()}, {@code Clock.system(ZoneId)},
 * {@code Clock.tickSeconds/tickMillis/tickMinutes(ZoneId)}, {@code InstantSource.system()}.
 * 주입된 시계를 감싸는 {@code Clock.tick(Clock, Duration)} 등은 허용</li>
 * <li>{@code System.currentTimeMillis()}, {@code Calendar.getInstance(..)}</li>
 * <li>현재 시각으로 만들어지는 생성자: 인자 없는 {@code new Date()},
 * 시간대·로캘만 받는 {@code new GregorianCalendar(..)}</li>
 * </ul>
 * 경과 시간 측정용 {@code System.nanoTime()}은 벽시계 시각이 아니라서 허용한다.
 * 시스템 시계는 Clock 설정 클래스 한 곳에서만 만든다.
 */
final class TimeRules {

	private static final Set<String> TIME_TYPES = Set.of("java.time.Instant", "java.time.LocalDate",
			"java.time.LocalDateTime", "java.time.LocalTime", "java.time.OffsetDateTime", "java.time.OffsetTime",
			"java.time.ZonedDateTime", "java.time.Year", "java.time.YearMonth", "java.time.MonthDay",
			"java.time.chrono.JapaneseDate", "java.time.chrono.HijrahDate", "java.time.chrono.MinguoDate",
			"java.time.chrono.ThaiBuddhistDate");

	private static final Set<String> SYSTEM_CLOCK_FACTORIES = Set.of("systemUTC", "systemDefaultZone", "system",
			"tickSeconds", "tickMillis", "tickMinutes");

	/** 이 타입들만 받는 GregorianCalendar 생성자는 현재 시각으로 만들어진다. */
	private static final Set<String> CURRENT_TIME_CALENDAR_PARAMETERS = Set.of("java.util.TimeZone",
			"java.util.Locale");

	private TimeRules() {
	}

	static ArchRule noDirectCurrentTime(String basePackage, String allowedClassName) {
		return noClasses().that()
			.resideInAPackage(basePackage + "..")
			.and()
			.doNotHaveFullyQualifiedName(allowedClassName)
			.should()
			.accessTargetWhere(DescribedPredicate.describe(
					"현재 시각을 직접 읽는 대상(now(), now(ZoneId), Clock.system*(), System.currentTimeMillis(), new Date(), Calendar.getInstance())",
					TimeRules::readsSystemTime))
			.because("시간은 주입된 Clock으로만 다룬다 (CLAUDE.md, S01). 시스템 시계는 " + allowedClassName + "에서만 만든다");
	}

	private static boolean readsSystemTime(JavaAccess<?> access) {
		if (!(access.getTarget() instanceof CodeUnitAccessTarget target)) {
			return false;
		}
		// 바이트코드의 소유 클래스는 호출에 쓴 이름이다. GregorianCalendar.getInstance()의 소유 클래스는
		// GregorianCalendar이고, Clock 하위 클래스 안의 systemUTC()는 그 하위 클래스다.
		// 그래서 정적 팩터리는 이름이 같은지가 아니라 상위 타입에 할당 가능한지로 본다.
		JavaClass owner = target.getOwner();
		String name = target.getName();
		List<String> parameterTypes = target.getRawParameterTypes().stream().map(JavaClass::getName).toList();
		boolean takesClock = parameterTypes.contains("java.time.Clock");
		if (TIME_TYPES.contains(owner.getName()) && name.equals("now")) {
			return !takesClock;
		}
		if (owner.isAssignableTo(Chronology.class) && name.equals("dateNow")) {
			return !takesClock;
		}
		if (owner.isAssignableTo(Clock.class) && SYSTEM_CLOCK_FACTORIES.contains(name)) {
			return true;
		}
		if (owner.isAssignableTo(InstantSource.class) && name.equals("system")) {
			return true;
		}
		if (owner.isAssignableTo(Calendar.class) && name.equals("getInstance")) {
			return true;
		}
		return switch (owner.getName()) {
			case "java.lang.System" -> name.equals("currentTimeMillis");
			case "java.util.Date" -> name.equals("<init>") && parameterTypes.isEmpty();
			case "java.util.GregorianCalendar" ->
				name.equals("<init>") && CURRENT_TIME_CALENDAR_PARAMETERS.containsAll(parameterTypes);
			default -> false;
		};
	}

}
