package com.duskafka.studybot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 엔티티 규칙이 비어 있어서 통과하는 게 아님을 보인다. 예제는 테스트 소스의
 * {@code com.duskafka.archfixtures.entities}에 있다 (앱의 베이스 패키지 밖이라 엔티티 스캔에 걸리지 않는다).
 */
class EntityRulesSelfTest {

	private static final String FIXTURES = "com.duskafka.archfixtures.entities";

	private static final JavaClasses CLASSES = new ClassFileImporter().importPackages(FIXTURES);

	@Test
	@DisplayName("S04: 엔티티의 public 기본 생성자는 걸리고, protected 기본 생성자는 통과한다")
	void publicNoArgConstructorIsDetected() {
		EvaluationResult result = EntityRules.noArgConstructorsAreProtected(FIXTURES).evaluate(CLASSES);

		assertThat(result.hasViolation()).isTrue();
		assertThat(result.getFailureReport().getDetails())
			.anySatisfy(detail -> assertThat(detail).contains("PublicNoArgConstructorEntity"))
			.noneSatisfy(detail -> assertThat(detail).contains("ProtectedEntity"));
	}

	@Test
	@DisplayName("S04: 엔티티의 public setter는 걸리고, 엔티티가 아닌 클래스의 setter는 걸리지 않는다")
	void publicSetterIsDetected() {
		EvaluationResult result = EntityRules.noPublicSetters(FIXTURES).evaluate(CLASSES);

		assertThat(result.hasViolation()).isTrue();
		assertThat(result.getFailureReport().getDetails())
			.anySatisfy(detail -> assertThat(detail).contains("PublicSetterEntity"))
			.noneSatisfy(detail -> assertThat(detail).contains("ProtectedEntity"))
			.noneSatisfy(detail -> assertThat(detail).contains("NotAnEntity"));
	}

}
