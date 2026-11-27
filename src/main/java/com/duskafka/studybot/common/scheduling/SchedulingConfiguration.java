package com.duskafka.studybot.common.scheduling;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러(@{@code Scheduled})와 ShedLock을 켠다 (S12 만들 것 1, 5.10 '판정 배치, ShedLock 설정'). 판정 배치(S12)와 알림 생성(S13, 5.8)이 쓴다.
 *
 * <ul>
 * <li>잠금은 {@code studybot.shedlock} 행이다 (V10). 서버가 여러 대여도 같은 이름의 작업은 한 번에 하나만 돈다.</li>
 * <li>잠금 시각은 DB 시각으로 센다 ({@code usingDbTime()}). 서버 시계가 서로 달라도 잠금 기준이 같다. 앱의 {@code Clock} 빈(테스트에서는
 * {@code MutableClock})과는 상관없다. 잠금은 실제로 흐르는 시간으로 재야 하기 때문이다.</li>
 * <li>기본 최대 잠금 시간은 30분이다. 작업마다 {@code @SchedulerLock}에서 다시 정한다. 서버가 작업 중에 죽으면 이 시간이 지나야 다른 서버가
 * 잡는다.</li>
 * </ul>
 *
 * <p>ShedLock은 {@code @SchedulerLock}이 붙은 빈 메서드를 프록시로 감싼다(기본 {@code PROXY_METHOD}). 같은 빈 안에서 자기 메서드를
 * 부르면 잠금을 거치지 않으니, 잠글 메서드는 바깥에서 부르는 진입점에 둔다.
 *
 * @see <a href="https://github.com/lukas-krecan/ShedLock">ShedLock README (7.11.0)</a>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulingConfiguration {

	/** 잠금 테이블. 스키마를 직접 적는다 (search_path에 맡기지 않음, application.yaml 'studybot 스키마'). */
	static final String LOCK_TABLE = "studybot.shedlock";

	@Bean
	LockProvider lockProvider(DataSource dataSource) {
		return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
			.withJdbcTemplate(new JdbcTemplate(dataSource))
			.withTableName(LOCK_TABLE)
			.usingDbTime()
			.build());
	}

}
