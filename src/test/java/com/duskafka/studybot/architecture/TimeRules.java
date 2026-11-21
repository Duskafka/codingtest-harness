package com.duskafka.studybot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Set;

/**
 * 시간 규칙: 시간은 주입된 {@link java.time.Clock}으로만 다룬다 (CLAUDE.md, S01 만들 것 3번).
 *
 * <p>금지하는 것 (호출과 메서드 참조 모두):
 * <ul>
 * <li>java.time 타입의 {@code now()}, {@code now(ZoneId)}. {@code now(Clock)}은 허용</li>
 * <li>{@code Clock.systemUTC()}, {@code Clock.systemDefaultZone()}, {@code Clock.system(ZoneId)}</li>
 * <li>{@code System.currentTimeMillis()}, 인자 없는 {@code new Date()}, {@code Calendar.getInstance(..)}</li>
 * </ul>
 * 경과 시간 측정용 {@code System.nanoTime()}은 벽시계 시각이 아니라서 허용한다.
 * 시스템 시계는 Clock 설정 클래스 한 곳에서만 만든다.
 */
final class TimeRules {

	private static final Set<String> TIME_TYPES = Set.of("java.time.Instant", "java.time.LocalDate",
			"java.time.LocalDateTime", "java.time.LocalTime", "java.time.OffsetDateTime", "java.time.OffsetTime",
			"java.time.ZonedDateTime", "java.time.Year", "java.time.YearMonth", "java.time.MonthDay");

	private static final Set<String> SYSTEM_CLOCK_FACTORIES = Set.of("systemUTC", "systemDefaultZone", "system");

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
		String owner = target.getOwner().getName();
		String name = target.getName();
		List<String> parameterTypes = target.getRawParameterTypes().stream().map(JavaClass::getName).toList();
		if (TIME_TYPES.contains(owner) && name.equals("now")) {
			return !parameterTypes.contains("java.time.Clock");
		}
		return switch (owner) {
			case "java.time.Clock" -> SYSTEM_CLOCK_FACTORIES.contains(name);
			case "java.lang.System" -> name.equals("currentTimeMillis");
			case "java.util.Calendar" -> name.equals("getInstance");
			case "java.util.Date" -> name.equals("<init>") && parameterTypes.isEmpty();
			default -> false;
		};
	}

}
