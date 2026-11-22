package com.duskafka.studybot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 모듈 규칙이 비어 있어서 통과하는 게 아님을 보인다 (S01 만들 것 2번).
 * 테스트 소스의 {@code com.duskafka.archfixtures} 아래에 규칙을 어기는 예제와 지키는 예제를 두고,
 * 같은 규칙을 그 예제에 적용한다. 예제는 앱의 베이스 패키지 밖에 있어서 엔티티·컴포넌트 스캔에 걸리지 않는다.
 */
class ArchitectureRulesSelfTest {

	private static final String FIXTURES = "com.duskafka.archfixtures.modules";

	@Test
	@DisplayName("S01: 4.2에 없는 방향(study → github)으로 의존하는 예제가 규칙에 걸린다")
	void forbiddenDirectionIsDetected() {
		String base = FIXTURES + ".forbidden";

		EvaluationResult result = evaluate(ModuleRules.moduleDependencies(base), base);

		assertThat(result.hasViolation()).isTrue();
		assertThat(result.getFailureReport().getDetails())
			.anySatisfy(detail -> assertThat(detail).contains("study → github").contains("StudyUsesGithub"));
	}

	@Test
	@DisplayName("S01: common이 다른 모듈(auth)에 의존하는 예제가 규칙에 걸린다")
	void commonDependingOnModuleIsDetected() {
		String base = FIXTURES + ".forbidden";

		EvaluationResult result = evaluate(ModuleRules.moduleDependencies(base), base);

		assertThat(result.getFailureReport().getDetails())
			.anySatisfy(detail -> assertThat(detail).contains("common → auth").contains("CommonUsesAuth"));
	}

	@Test
	@DisplayName("S01: 4.2에 있는 방향, common·auth 공개 API, 같은 모듈의 내부 패키지는 통과한다")
	void allowedDependenciesPass() {
		String base = FIXTURES + ".allowed";

		assertThat(evaluate(ModuleRules.moduleDependencies(base), base).hasViolation()).isFalse();
		assertThat(evaluate(ModuleRules.noAccessToOtherModulesInternals(base), base).hasViolation()).isFalse();
	}

	@Test
	@DisplayName("S01: 허용된 방향이라도 다른 모듈의 내부(하위) 패키지에 접근하면 걸린다")
	void accessToOtherModuleInternalsIsDetected() {
		String base = FIXTURES + ".internal";

		assertThat(evaluate(ModuleRules.moduleDependencies(base), base).hasViolation())
			.as("notification → study 방향 자체는 허용")
			.isFalse();

		EvaluationResult result = evaluate(ModuleRules.noAccessToOtherModulesInternals(base), base);

		assertThat(result.hasViolation()).isTrue();
		assertThat(result.getFailureReport().getDetails())
			.allSatisfy(detail -> assertThat(detail).contains("NotificationUsesStudyInternal").contains("StudyRecord"))
			.noneSatisfy(detail -> assertThat(detail).contains("StudyFacade"));
	}

	@Test
	@DisplayName("S01: 엔티티·저장소가 모듈의 루트(공개) 패키지에 있으면 걸리고, 하위 패키지면 통과한다")
	void entitiesAndRepositoriesMustBeInternal() {
		String base = FIXTURES + ".entity";

		EvaluationResult result = evaluate(ModuleRules.entitiesAndRepositoriesAreInternal(base), base);

		assertThat(result.hasViolation()).isTrue();
		assertThat(result.getFailureReport().getDetails())
			.anySatisfy(detail -> assertThat(detail).contains("StudyEntityInRoot"))
			.anySatisfy(detail -> assertThat(detail).contains("ProgressRepositoryInRoot"))
			.noneSatisfy(detail -> assertThat(detail).contains("StudyRow"));
	}

	@Test
	@DisplayName("S01: 모듈 사이의 순환 의존(study ↔ github)이 걸린다")
	void cyclesAreDetected() {
		String base = FIXTURES + ".cycle";

		EvaluationResult result = evaluate(ModuleRules.noCycles(base), base);

		assertThat(result.hasViolation()).isTrue();
		assertThat(result.getFailureReport().getDetails())
			.anySatisfy(detail -> assertThat(detail).contains("CycleStudy").contains("CycleGithub"));
	}

	@Test
	@DisplayName("S01: 모듈도 루트 패키지도 아닌 최상위 패키지의 클래스가 걸린다")
	void classesOutsideModulesAreDetected() {
		String base = FIXTURES + ".stray";

		EvaluationResult result = evaluate(ModuleRules.everyClassBelongsToAModule(base), base);

		assertThat(result.hasViolation()).isTrue();
		assertThat(result.getFailureReport().getDetails())
			.anySatisfy(detail -> assertThat(detail).contains("StrayHelper"))
			.noneSatisfy(detail -> assertThat(detail).contains("RootApplication"))
			.noneSatisfy(detail -> assertThat(detail).contains("StudyThing"));
	}

	private static EvaluationResult evaluate(ArchRule rule, String basePackage) {
		JavaClasses classes = new ClassFileImporter().importPackages(basePackage);
		assertThat(classes).as("예제 클래스를 가져와야 한다: " + basePackage).isNotEmpty();
		return rule.evaluate(classes);
	}

}
