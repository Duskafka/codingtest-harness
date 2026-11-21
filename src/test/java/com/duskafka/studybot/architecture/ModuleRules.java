package com.duskafka.studybot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.repository.Repository;

/**
 * 모듈 구조 규칙 (docs/spec/architecture.md 4.2, docs/decisions/001-module-boundaries.md).
 *
 * <ul>
 * <li>모듈은 {@code <베이스 패키지>.<모듈>} 패키지다. 모듈의 루트 패키지가 공개 API이고, 하위 패키지는 내부다.</li>
 * <li>다른 모듈에는 4.2 그래프에 직접 그려진 화살표 방향으로만 의존한다. 여기에 결정 제안서 001의 해석을 더한다:
 * 모든 모듈은 {@code common}과 {@code auth}의 공개 API에 의존할 수 있고, {@code common}은 다른 모듈에,
 * {@code auth}는 {@code common} 말고는 의존하지 않는다.</li>
 * </ul>
 *
 * 규칙은 베이스 패키지를 인자로 받는다. 실제 코드({@link ArchitectureTest})와 규칙을 어기는 예제
 * ({@link ArchitectureRulesSelfTest})에 같은 규칙을 적용하기 위해서다.
 */
final class ModuleRules {

	static final String BASE_PACKAGE = "com.duskafka.studybot";

	/** 모듈마다 의존해도 되는 다른 모듈. 4.2 그래프의 직접 화살표 + common·auth (결정 제안서 001). */
	static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.ofEntries(
			Map.entry("common", Set.of()),
			Map.entry("auth", Set.of("common")),
			Map.entry("study", Set.of("common", "auth")),
			Map.entry("github", Set.of("verification", "common", "auth")),
			Map.entry("verification", Set.of("progress", "common", "auth")),
			Map.entry("progress", Set.of("study", "common", "auth")),
			Map.entry("judgment", Set.of("progress", "ledger", "common", "auth")),
			Map.entry("ledger", Set.of("common", "auth")),
			Map.entry("appeal", Set.of("judgment", "common", "auth")),
			Map.entry("notification", Set.of("progress", "study", "common", "auth")),
			Map.entry("discord", Set.of("progress", "notification", "common", "auth")));

	/** 하위 패키지까지 전부 공개인 모듈. common은 모두가 쓰는 도구 모음이다. */
	static final Set<String> FULLY_PUBLIC_MODULES = Set.of("common");

	/**
	 * 다른 모듈에 공개하는 하위 패키지 ({@code <모듈>.<하위 패키지>}). 아직 없다.
	 * 예: S08에서 {@code verification.source}를 공개하기로 하면 여기에 더하고 PR 판단 기록에 남긴다.
	 */
	static final Set<String> EXPOSED_SUBPACKAGES = Set.of();

	private ModuleRules() {
	}

	/** 다른 모듈에는 허용된 방향으로만 의존한다. */
	static ArchRule moduleDependencies(String basePackage) {
		return classes().that(resideInAModule(basePackage))
			.should(new ArchCondition<JavaClass>("4.2 그래프의 화살표 방향(+ common·auth)으로만 다른 모듈에 의존한다") {
				@Override
				public void check(JavaClass origin, ConditionEvents events) {
					String from = moduleOf(basePackage, origin.getPackageName()).orElseThrow();
					for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
						Optional<String> to = moduleOf(basePackage, targetPackage(dependency));
						if (to.isPresent() && !to.get().equals(from)
								&& !ALLOWED_DEPENDENCIES.get(from).contains(to.get())) {
							events.add(SimpleConditionEvent.violated(dependency,
									from + " → " + to.get() + " 의존은 허용되지 않아요: " + dependency.getDescription()));
						}
					}
				}
			})
			// 모듈이 아직 비어 있다. 규칙이 실제로 걸리는지는 ArchitectureRulesSelfTest가 보인다.
			.allowEmptyShould(true);
	}

	/** 모듈 사이에 순환 의존이 없다. 허용 목록이 순환을 만들지 않는지 한 번 더 지킨다. */
	static ArchRule noCycles(String basePackage) {
		return slices().matching(basePackage + ".(*)..")
			.should()
			.beFreeOfCycles()
			.allowEmptyShould(true);
	}

	/** 다른 모듈의 내부(하위) 패키지에 의존하지 않는다. 공개 API는 모듈의 루트 패키지에 있다. */
	static ArchRule noAccessToOtherModulesInternals(String basePackage) {
		return classes().that(resideInOrUnder(basePackage))
			.should(new ArchCondition<JavaClass>("다른 모듈의 내부(하위) 패키지에 의존하지 않는다") {
				@Override
				public void check(JavaClass origin, ConditionEvents events) {
					Optional<String> from = moduleOf(basePackage, origin.getPackageName());
					for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
						String targetPackage = targetPackage(dependency);
						Optional<String> to = moduleOf(basePackage, targetPackage);
						if (to.isEmpty() || to.equals(from) || FULLY_PUBLIC_MODULES.contains(to.get())) {
							continue;
						}
						if (isInternalPackage(basePackage, to.get(), targetPackage)) {
							events.add(SimpleConditionEvent.violated(dependency,
									to.get() + " 모듈의 내부 패키지(" + targetPackage + ")에 접근해요. 공개 API(모듈 루트 패키지)를 쓰세요: "
											+ dependency.getDescription()));
						}
					}
				}
			})
			.allowEmptyShould(true);
	}

	/** 엔티티와 저장소는 모듈의 하위(내부) 패키지에만 둔다. 다른 모듈이 직접 쓰지 못하게 하기 위해서다. */
	static ArchRule entitiesAndRepositoriesAreInternal(String basePackage) {
		DescribedPredicate<JavaClass> entitiesOrRepositories = DescribedPredicate.describe(
				"엔티티(@Entity)나 Spring Data 저장소",
				javaClass -> javaClass.isAnnotatedWith(Entity.class) || javaClass.isAssignableTo(Repository.class));
		return classes().that(resideInAModule(basePackage))
			.and(entitiesOrRepositories)
			.should(new ArchCondition<JavaClass>("모듈의 하위(내부) 패키지에 있다") {
				@Override
				public void check(JavaClass javaClass, ConditionEvents events) {
					String module = moduleOf(basePackage, javaClass.getPackageName()).orElseThrow();
					if (javaClass.getPackageName().equals(basePackage + "." + module)) {
						events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + "는 " + module
								+ " 모듈의 루트(공개) 패키지에 있어요. 엔티티·저장소는 하위 패키지에 둬요"));
					}
				}
			})
			.allowEmptyShould(true);
	}

	/** 베이스 패키지 아래의 클래스는 루트 패키지(애플리케이션 클래스)나 모듈 패키지에만 있다. */
	static ArchRule everyClassBelongsToAModule(String basePackage) {
		return classes().that(resideInOrUnder(basePackage))
			.should(new ArchCondition<JavaClass>("루트 패키지나 4.2의 모듈 패키지에 있다") {
				@Override
				public void check(JavaClass javaClass, ConditionEvents events) {
					String packageName = javaClass.getPackageName();
					if (!packageName.equals(basePackage) && moduleOf(basePackage, packageName).isEmpty()) {
						events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName()
								+ "는 4.2에 없는 패키지(" + packageName + ")에 있어요. 모듈 패키지에 둬요"));
					}
				}
			});
	}

	private static Optional<String> moduleOf(String basePackage, String packageName) {
		return ALLOWED_DEPENDENCIES.keySet()
			.stream()
			.filter(module -> isSameOrSubPackage(packageName, basePackage + "." + module))
			.findFirst();
	}

	private static boolean isInternalPackage(String basePackage, String module, String packageName) {
		String moduleRoot = basePackage + "." + module;
		if (packageName.equals(moduleRoot)) {
			return false;
		}
		return EXPOSED_SUBPACKAGES.stream()
			.noneMatch(exposed -> isSameOrSubPackage(packageName, basePackage + "." + exposed));
	}

	private static boolean isSameOrSubPackage(String packageName, String parent) {
		return packageName.equals(parent) || packageName.startsWith(parent + ".");
	}

	private static String targetPackage(Dependency dependency) {
		return dependency.getTargetClass().getBaseComponentType().getPackageName();
	}

	private static DescribedPredicate<JavaClass> resideInAModule(String basePackage) {
		return DescribedPredicate.describe("모듈 패키지에 있는 클래스",
				javaClass -> moduleOf(basePackage, javaClass.getPackageName()).isPresent());
	}

	private static DescribedPredicate<JavaClass> resideInOrUnder(String basePackage) {
		return DescribedPredicate.describe(basePackage + " 아래의 클래스",
				javaClass -> isSameOrSubPackage(javaClass.getPackageName(), basePackage));
	}

}
