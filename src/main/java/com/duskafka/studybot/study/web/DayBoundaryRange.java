package com.duskafka.studybot.study.web;

import com.duskafka.studybot.common.time.StudyTimeRule;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalTime;

/** 하루 기준 시각은 00:00~06:00 (R1). 범위는 {@link StudyTimeRule#MAX_DAY_BOUNDARY}와 같다. null은 통과(기본값을 쓴다). */
@Documented
@Constraint(validatedBy = DayBoundaryRange.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@interface DayBoundaryRange {

	String message() default "하루 기준 시각은 00:00~06:00이어야 해요";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<DayBoundaryRange, LocalTime> {

		@Override
		public boolean isValid(LocalTime value, ConstraintValidatorContext context) {
			return value == null || !value.isAfter(StudyTimeRule.MAX_DAY_BOUNDARY);
		}

	}

}
