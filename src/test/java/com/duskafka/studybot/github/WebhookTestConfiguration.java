package com.duskafka.studybot.github;

import com.duskafka.studybot.github.webhook.GithubEvent;
import com.duskafka.studybot.github.webhook.GithubEventHandler;
import com.duskafka.studybot.github.webhook.GithubWebhookEvent;
import com.duskafka.studybot.github.webhook.HandlingResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * 웹훅 통합 테스트용 빈. 웹훅 테스트 클래스는 모두 이 설정 하나만 더해서 Spring 컨텍스트를 함께 쓴다.
 *
 * <ul>
 * <li>{@link RecordingPushHandler}: S08 전까지 push 처리기 자리를 채우는 테스트 처리기. 불린 횟수와 받은 값을 남기고,
 * 테스트가 정한 대로 결과를 돌려주거나 예외를 던진다.</li>
 * <li>{@link DatabaseOutage}: 앱의 DataSource를 감싸서, 켜면 새 DB 연결을 얻지 못하게 한다 (DB 장애 흉내).</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class WebhookTestConfiguration {

	@Bean
	RecordingPushHandler recordingPushHandler() {
		return new RecordingPushHandler();
	}

	@Bean
	static DatabaseOutage databaseOutage() {
		return new DatabaseOutage();
	}

	/** push 처리기 자리의 테스트 처리기. 테스트마다 {@link #reset()}한다. */
	public static class RecordingPushHandler implements GithubEventHandler {

		private final List<GithubWebhookEvent> calls = new CopyOnWriteArrayList<>();

		private volatile Function<GithubWebhookEvent, HandlingResult> behavior = event -> HandlingResult.PROCESSED;

		@Override
		public GithubEvent event() {
			return GithubEvent.PUSH;
		}

		@Override
		public HandlingResult handle(GithubWebhookEvent event) {
			calls.add(event);
			return behavior.apply(event);
		}

		public List<GithubWebhookEvent> calls() {
			return List.copyOf(calls);
		}

		public void willDo(Function<GithubWebhookEvent, HandlingResult> newBehavior) {
			this.behavior = newBehavior;
		}

		public void reset() {
			calls.clear();
			behavior = event -> HandlingResult.PROCESSED;
		}

	}

	/**
	 * DB 장애 흉내. 앱의 {@link DataSource} 빈을 감싸고, {@link #start()} 하면 새 연결을 달라는 요청이
	 * {@link SQLTransientConnectionException}으로 실패한다 (연결 풀이 DB에 닿지 못할 때와 같은 예외 종류).
	 * 이미 트랜잭션이 잡은 연결은 그대로 쓴다. 테스트마다 {@link #stop()}한다.
	 */
	public static class DatabaseOutage implements BeanPostProcessor {

		private final AtomicBoolean down = new AtomicBoolean();

		public void start() {
			down.set(true);
		}

		public void stop() {
			down.set(false);
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (bean instanceof DataSource dataSource) {
				return new DelegatingDataSource(dataSource) {

					@Override
					public Connection getConnection() throws SQLException {
						failIfDown();
						return super.getConnection();
					}

					@Override
					public Connection getConnection(String username, String password) throws SQLException {
						failIfDown();
						return super.getConnection(username, password);
					}

				};
			}
			return bean;
		}

		private void failIfDown() throws SQLTransientConnectionException {
			if (down.get()) {
				throw new SQLTransientConnectionException("테스트: DB에 연결할 수 없어요");
			}
		}

	}

}
