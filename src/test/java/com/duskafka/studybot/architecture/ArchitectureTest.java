package com.duskafka.studybot.architecture;

import static com.duskafka.studybot.architecture.ModuleRules.BASE_PACKAGE;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * main 코드가 모듈 구조 규칙을 지키는지 검사한다 (docs/spec/architecture.md 4.2, 8.1 '구조').
 * 규칙 자체가 실제로 걸린다는 것은 {@link ArchitectureRulesSelfTest}가 보인다.
 */
class ArchitectureTest {

	/** 시스템 시계를 만들어도 되는 유일한 클래스. */
	private static final String CLOCK_CONFIGURATION = BASE_PACKAGE + ".common.time.ClockConfiguration";

	private static JavaClasses mainClasses;

	@BeforeAll
	static void importMainClasses() {
		mainClasses = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(BASE_PACKAGE);
	}

	@Test
	@DisplayName("S01: 다른 모듈에는 4.2의 화살표 방향(+ common·auth)으로만 의존한다")
	void moduleDependencies() {
		ModuleRules.moduleDependencies(BASE_PACKAGE).check(mainClasses);
	}

	@Test
	@DisplayName("S01: 모듈 사이에 순환 의존이 없다")
	void noCycles() {
		ModuleRules.noCycles(BASE_PACKAGE).check(mainClasses);
	}

	@Test
	@DisplayName("S01: 다른 모듈의 내부(하위) 패키지에 접근하지 않는다")
	void noAccessToOtherModulesInternals() {
		ModuleRules.noAccessToOtherModulesInternals(BASE_PACKAGE).check(mainClasses);
	}

	@Test
	@DisplayName("S01: 엔티티와 저장소는 모듈의 하위(내부) 패키지에 있다")
	void entitiesAndRepositoriesAreInternal() {
		ModuleRules.entitiesAndRepositoriesAreInternal(BASE_PACKAGE).check(mainClasses);
	}

	@Test
	@DisplayName("S04: 엔티티 기본 생성자는 protected다 (결정 제안서 003)")
	void entityNoArgConstructorsAreProtected() {
		EntityRules.noArgConstructorsAreProtected(BASE_PACKAGE).check(mainClasses);
	}

	@Test
	@DisplayName("S04: 엔티티에 public setter가 없다 (결정 제안서 003)")
	void entitiesHaveNoPublicSetters() {
		EntityRules.noPublicSetters(BASE_PACKAGE).check(mainClasses);
	}

	@Test
	@DisplayName("S01: main 클래스는 루트 패키지나 모듈 패키지에만 있다")
	void everyClassBelongsToAModule() {
		ModuleRules.everyClassBelongsToAModule(BASE_PACKAGE).check(mainClasses);
	}

	@Test
	@DisplayName("S01: main 코드는 현재 시각을 직접 읽지 않는다 (Clock 설정 클래스만 예외)")
	void noDirectCurrentTime() {
		assertThat(mainClasses.contain(CLOCK_CONFIGURATION))
			.as("예외로 둔 Clock 설정 클래스가 실제로 있어야 한다: " + CLOCK_CONFIGURATION)
			.isTrue();
		TimeRules.noDirectCurrentTime(BASE_PACKAGE, CLOCK_CONFIGURATION).check(mainClasses);
	}

}
