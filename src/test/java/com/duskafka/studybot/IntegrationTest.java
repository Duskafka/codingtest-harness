package com.duskafka.studybot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;

/**
 * 통합 테스트 묶음: 앱 전체 컨텍스트, Testcontainers PostgreSQL, MockMvc, 테스트용 시계({@link MutableClock}).
 *
 * <p>같은 설정을 쓰는 테스트끼리 Spring 컨텍스트(와 DB 컨테이너)를 공유한다.
 * 테스트 클래스에 {@code @Import}나 {@code @TestPropertySource}를 더하면 컨텍스트가 따로 만들어진다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class })
@TestExecutionListeners(listeners = MutableClockResetListener.class, mergeMode = MergeMode.MERGE_WITH_DEFAULTS)
public @interface IntegrationTest {

}
