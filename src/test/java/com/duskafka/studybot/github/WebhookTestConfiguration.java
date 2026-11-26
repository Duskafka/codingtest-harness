package com.duskafka.studybot.github;

import com.duskafka.studybot.github.webhook.GithubEvent;
import com.duskafka.studybot.github.webhook.GithubEventHandler;
import com.duskafka.studybot.github.webhook.GithubWebhookEvent;
import com.duskafka.studybot.github.webhook.HandlingResult;
import com.duskafka.studybot.progress.SolveToAssign;
import com.duskafka.studybot.progress.StudyDayAssignment;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * 웹훅 통합 테스트용 빈. 웹훅 테스트 클래스는 모두 이 설정 하나만 더해서 Spring 컨텍스트를 함께 쓴다.
 *
 * <ul>
 * <li>{@link RecordingPushHandler}: 앱의 push 처리기(S08)를 감싸는 테스트 처리기. 불린 횟수와 받은 값을 남기고, 테스트가 정한 대로
 * 결과를 돌려주거나 예외를 던진다. 기본은 실제 처리기를 부르지 않고 PROCESSED다 (S06 테스트가 수신기만 보게).
 * S08 테스트는 {@link RecordingPushHandler#passThrough()}로 실제 처리기를 부른다.</li>
 * <li>{@link RecordingStudyDayAssignment}: 앱의 배정 연결 지점(S08 만들 것 6)을 감싸서 불린 값을 남기고, 정하면 예외를 던진다.</li>
 * <li>{@link DatabaseOutage}: 앱의 DataSource를 감싸서, 켜면 새 DB 연결을 얻지 못하게 한다 (DB 장애 흉내).</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class WebhookTestConfiguration {

	@Bean
	static RecordingPushHandler recordingPushHandler() {
		return new RecordingPushHandler();
	}

	@Bean
	static RecordingStudyDayAssignment recordingStudyDayAssignment() {
		return new RecordingStudyDayAssignment();
	}

	@Bean
	static DatabaseOutage databaseOutage() {
		return new DatabaseOutage();
	}

	/**
	 * push 처리기 자리의 테스트 처리기. 앱의 push 처리기 빈을 감싼 처리기로 바꿔 끼운다 (처리기는 이벤트당 하나라 따로 더하지 않는다).
	 * 테스트마다 {@link #reset()}한다.
	 */
	public static class RecordingPushHandler implements BeanPostProcessor {

		private final List<GithubWebhookEvent> calls = new CopyOnWriteArrayList<>();

		private volatile Function<GithubWebhookEvent, HandlingResult> behavior = event -> HandlingResult.PROCESSED;

		private volatile @Nullable GithubEventHandler real;

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (bean instanceof GithubEventHandler handler && handler.event() == GithubEvent.PUSH) {
				if (this.real != null) {
					throw new IllegalStateException("push 처리기가 둘이에요: " + beanName);
				}
				this.real = handler;
				return new GithubEventHandler() {

					@Override
					public GithubEvent event() {
						return GithubEvent.PUSH;
					}

					@Override
					public HandlingResult handle(GithubWebhookEvent event) {
						calls.add(event);
						return behavior.apply(event);
					}

				};
			}
			return bean;
		}

		public List<GithubWebhookEvent> calls() {
			return List.copyOf(calls);
		}

		public void willDo(Function<GithubWebhookEvent, HandlingResult> newBehavior) {
			this.behavior = newBehavior;
		}

		/** 앱의 실제 push 처리기를 부른다. {@link #willDo}에서 앞뒤로 다른 일을 끼울 때 쓴다. */
		public HandlingResult delegate(GithubWebhookEvent event) {
			GithubEventHandler handler = this.real;
			if (handler == null) {
				throw new IllegalStateException("앱에 push 처리기가 없어요");
			}
			return handler.handle(event);
		}

		/** 이후 push는 앱의 실제 처리기가 처리한다. */
		public void passThrough() {
			this.behavior = this::delegate;
		}

		public void reset() {
			calls.clear();
			behavior = event -> HandlingResult.PROCESSED;
		}

	}

	/** 배정 연결 지점({@link StudyDayAssignment})을 감싼다. 테스트마다 {@link #reset()}한다. */
	public static class RecordingStudyDayAssignment implements BeanPostProcessor {

		private final List<SolveToAssign> calls = new CopyOnWriteArrayList<>();

		private volatile @Nullable RuntimeException failure;

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (bean instanceof StudyDayAssignment real) {
				return new StudyDayAssignment() {

					@Override
					public void assign(SolveToAssign solve) {
						calls.add(solve);
						RuntimeException toThrow = RecordingStudyDayAssignment.this.failure;
						if (toThrow != null) {
							throw toThrow;
						}
						real.assign(solve);
					}

					/** 확인 대기 지정(S10)은 push 처리 기록과 섞지 않고 그대로 넘긴다. */
					@Override
					public void assignIdentified(SolveToAssign solve) {
						real.assignIdentified(solve);
					}

				};
			}
			return bean;
		}

		public List<SolveToAssign> calls() {
			return List.copyOf(calls);
		}

		/** 이후 불리면 이 예외를 던진다. */
		public void willThrow(RuntimeException exception) {
			this.failure = exception;
		}

		public void reset() {
			calls.clear();
			failure = null;
		}

	}

	/**
	 * DB 장애 흉내. 앱의 {@link DataSource} 빈을 감싸고, {@link #start()} 하면 새 연결을 달라는 요청과 이미 받아 둔 연결의 사용이
	 * 모두 {@link SQLTransientConnectionException}으로 실패한다 (DB에 닿지 못할 때와 같은 예외 종류). 연결을 풀에 돌려주는
	 * {@code close()}는 그대로 된다. 테스트마다 {@link #stop()}한다.
	 *
	 * <p>이미 받아 둔 연결도 막는 이유: {@code spring.jpa.open-in-view}가 켜져 있어 한 요청 안의 트랜잭션들이 요청에 묶인
	 * EntityManager와 그 연결을 이어 쓴다. 새 연결만 막으면 요청 중간에 난 장애를 흉내 낼 수 없다.
	 */
	public static class DatabaseOutage implements BeanPostProcessor {

		private static final Set<String> ALWAYS_ALLOWED = Set.of("close", "isClosed", "unwrap", "isWrapperFor", "toString",
				"hashCode", "equals");

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
						return guard(super.getConnection());
					}

					@Override
					public Connection getConnection(String username, String password) throws SQLException {
						failIfDown();
						return guard(super.getConnection(username, password));
					}

				};
			}
			return bean;
		}

		/** 장애 중에는 연결을 쓰는 모든 호출이 실패한다. 풀에 돌려주는 호출과 객체 기본 메서드만 그대로 둔다. */
		private Connection guard(Connection connection) {
			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
					(proxy, method, args) -> {
						if (!ALWAYS_ALLOWED.contains(method.getName())) {
							failIfDown();
						}
						try {
							return method.invoke(connection, args);
						}
						catch (InvocationTargetException ex) {
							throw ex.getCause();
						}
					});
		}

		private void failIfDown() throws SQLTransientConnectionException {
			if (down.get()) {
				throw new SQLTransientConnectionException("테스트: DB에 연결할 수 없어요");
			}
		}

	}

}
