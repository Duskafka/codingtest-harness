package com.duskafka.studybot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

/**
 * 엔티티 규칙 (결정 제안서 003 '검증 방법'의 엔티티 규칙, S04 PR 판단 기록).
 *
 * <ul>
 * <li>기본 생성자는 protected다. JPA는 쓰고, 앱 코드는 빈 엔티티를 만들지 못한다
 * ({@code @NoArgsConstructor(access = AccessLevel.PROTECTED)}).</li>
 * <li>public setter가 없다. 값은 생성자나 의미 있는 메서드로만 바꾼다. Lombok {@code @Setter}·{@code @Data}는
 * {@code lombok.config}가 막고, 손으로 쓴 setter는 이 규칙이 막는다.</li>
 * </ul>
 *
 * 규칙이 실제로 걸리는지는 {@link EntityRulesSelfTest}가 보인다.
 */
final class EntityRules {

	private static final DescribedPredicate<JavaConstructor> PARAMETERLESS = DescribedPredicate
		.describe("파라미터가 없는", constructor -> constructor.getRawParameterTypes().isEmpty());

	private EntityRules() {
	}

	static ArchRule noArgConstructorsAreProtected(String basePackage) {
		return constructors().that()
			.areDeclaredInClassesThat()
			.resideInAPackage(basePackage + "..")
			.and()
			.areDeclaredInClassesThat()
			.areAnnotatedWith(Entity.class)
			.and(PARAMETERLESS)
			.should()
			.beProtected()
			.because("엔티티 기본 생성자는 JPA만 쓰게 protected로 둔다 (결정 제안서 003)")
			.allowEmptyShould(true);
	}

	static ArchRule noPublicSetters(String basePackage) {
		return methods().that()
			.areDeclaredInClassesThat()
			.resideInAPackage(basePackage + "..")
			.and()
			.areDeclaredInClassesThat()
			.areAnnotatedWith(Entity.class)
			.and()
			// setName처럼 set 뒤가 대문자인 것만 setter로 본다. settle·setup 같은 도메인 메서드는 걸리지 않는다.
			.haveNameMatching("set[A-Z].*")
			.should()
			.notBePublic()
			.because("엔티티 값은 의미 있는 메서드로만 바꾼다 (결정 제안서 003)")
			.allowEmptyShould(true);
	}

}
