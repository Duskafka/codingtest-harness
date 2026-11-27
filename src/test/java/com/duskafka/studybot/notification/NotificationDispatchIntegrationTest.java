package com.duskafka.studybot.notification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.notification.NotificationRows.NotificationRow;
import com.duskafka.studybot.notification.dispatch.NotificationDispatcher;
import com.duskafka.studybot.notification.reminder.ReminderGenerator;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 발송기 (S13 만들 것 3, 5.8, T16). 웹 푸시 서비스는 WireMock이다 ({@link WebPushStub}).
 *
 * <p>발송기는 DB 전체에서 보낼 행을 고르므로 테스트마다 다른 테스트가 남긴 발송 대기 행을 만료로 바꾼다
 * ({@link NotificationRows#expireLeftovers}). 기본 스터디는 11/20 알림 시각이 22:00, 마감이 11/21 00:00이다.
 */
@IntegrationTest
class NotificationDispatchIntegrationTest {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private ReminderGenerator generator;

	@Autowired
	private NotificationDispatcher dispatcher;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private StudyDayRows rows;

	private NotificationRows notifications;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		notifications = new NotificationRows(jdbc);
		notifications.expireLeftovers();
	}

	/** 남은 멤버 하나와 그 멤버의 구독(201로 답함)이 있는 스터디에서 11/20 알림을 만든다. */
	private Reminded remindedAlice(String login) {
		long alice = rows.newUser(login);
		TestStudy study = rows.standardStudy(alice);
		String endpoint = WebPushStub.newEndpoint();
		WebPushStub.reply(endpoint, 201);
		notifications.insertSubscription(alice, endpoint, PushReceiver.create());
		clock.setInstant(seoul(NOV_20, 22, 0));
		generator.remindStudy(study.studyId());
		NotificationRow reminder = notifications.ofStudy(study.studyId(), "REMINDER").getFirst();
		return new Reminded(study.studyId(), alice, endpoint, reminder.id());
	}

	private record Reminded(long studyId, long userId, String endpoint, long notificationId) {
	}

	@Test
	@DisplayName("T16: 발송 직전에 목표를 달성했으면 SKIPPED이고 보내지 않는다")
	void t16SolvedBeforeSendingIsSkipped() {
		Reminded reminded = remindedAlice("s13-skip-solved");
		rows.putMemberDay(reminded.studyId(), reminded.userId(), NOV_20, 1, null, null);

		dispatcher.run();

		assertThat(notifications.byId(reminded.notificationId()).status()).isEqualTo("SKIPPED");
		assertThat(WebPushStub.requestsTo(reminded.endpoint())).isEmpty();
	}

	@Test
	@DisplayName("T16: 발송 직전에 면제권을 썼으면 SKIPPED다 (알려진 충돌 10번: 면제권은 발송 직전 재확인으로 뺀다)")
	void t16ExemptedBeforeSendingIsSkipped() {
		Reminded reminded = remindedAlice("s13-skip-exempt");
		rows.putMemberDay(reminded.studyId(), reminded.userId(), NOV_20, 0, null, seoul(NOV_20, 22, 1));

		dispatcher.run();

		assertThat(notifications.byId(reminded.notificationId()).status()).isEqualTo("SKIPPED");
		assertThat(WebPushStub.requestsTo(reminded.endpoint())).isEmpty();
	}

	@Test
	@DisplayName("아직 남았으면 보내고 SENT다")
	void stillRemainingIsSent() {
		Reminded reminded = remindedAlice("s13-send");

		dispatcher.run();

		NotificationRow row = notifications.byId(reminded.notificationId());
		assertThat(row.status()).isEqualTo("SENT");
		assertThat(row.sentAt()).isEqualTo(seoul(NOV_20, 22, 0));
		assertThat(row.attempts()).isEqualTo(1);
		assertThat(row.lockedUntil()).isNull();
		assertThat(WebPushStub.requestsTo(reminded.endpoint())).hasSize(1);
	}

	@Test
	@DisplayName("서버가 알림 시각부터 마감 뒤까지 꺼져 있었다면 켜질 때 남은 발송 대기 알림은 보내지 않고 EXPIRED로 둔다")
	void pendingPastDeadlineIsExpired() {
		Reminded reminded = remindedAlice("s13-expired");
		clock.setInstant(seoul(NOV_21, 0, 0));

		dispatcher.run();

		assertThat(notifications.byId(reminded.notificationId()).status()).isEqualTo("EXPIRED");
		assertThat(WebPushStub.requestsTo(reminded.endpoint())).isEmpty();
	}

	@Test
	@DisplayName("발송 중에 서버가 죽은 것처럼 SENDING이 남으면 잠금 만료 뒤 다시 PENDING이 된다. 만료 전이면 그대로다")
	void staleSendingReturnsToPendingAfterLockExpires() {
		long alice = rows.newUser("s13-stale");
		TestStudy study = rows.standardStudy(alice);
		Instant now = seoul(NOV_20, 22, 10);
		clock.setInstant(now);
		// 다시 가져가지 않게 다음 시도 시각을 뒤로 둔다. 되돌림만 본다.
		long stale = notifications.insertPending("SHARE_PROMPT", study.studyId(), alice, NOV_20, "{}",
				now.plus(Duration.ofMinutes(1)), seoul(NOV_21, 0, 0));
		notifications.markSending(stale, now.minusSeconds(1));
		long held = notifications.insertPending("SHARE_PROMPT", study.studyId(), alice, NOV_20, "{}",
				now.plus(Duration.ofMinutes(1)), seoul(NOV_21, 0, 0));
		notifications.markSending(held, now.plusSeconds(30));

		dispatcher.run();

		NotificationRow released = notifications.byId(stale);
		assertThat(released.status()).isEqualTo("PENDING");
		assertThat(released.lockedUntil()).isNull();
		assertThat(released.attempts()).isZero();
		assertThat(notifications.byId(held).status()).isEqualTo("SENDING");
	}

	@Test
	@DisplayName("다른 발송기가 잠근 행은 건너뛴다 (FOR UPDATE SKIP LOCKED)")
	void rowLockedByAnotherDispatcherIsSkipped() throws Exception {
		Reminded reminded = remindedAlice("s13-skip-locked");
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> other = executor.submit(() -> transaction.executeWithoutResult(status -> {
				jdbc.sql("select id from studybot.notifications where id = :id for update")
					.param("id", reminded.notificationId())
					.query(Long.class)
					.single();
				locked.countDown();
				try {
					release.await(30, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			dispatcher.run();

			assertThat(notifications.byId(reminded.notificationId()).status()).isEqualTo("PENDING");
			assertThat(WebPushStub.requestsTo(reminded.endpoint())).isEmpty();
			release.countDown();
			other.get(10, TimeUnit.SECONDS);
		}
		finally {
			release.countDown();
			executor.shutdownNow();
		}

		dispatcher.run();

		assertThat(notifications.byId(reminded.notificationId()).status()).isEqualTo("SENT");
	}

	@Test
	@DisplayName("다음 시도 시각 전인 행은 가져가지 않는다")
	void notDueYetIsLeftAlone() {
		long alice = rows.newUser("s13-not-due");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), rows.newUser("s13-not-due-other"), NOV_1);
		Instant now = seoul(NOV_20, 22, 10);
		clock.setInstant(now);
		long later = notifications.insertPending("SHARE_PROMPT", study.studyId(), alice, NOV_20, "{}",
				now.plusSeconds(1), seoul(NOV_21, 0, 0));

		dispatcher.run();

		assertThat(notifications.byId(later).status()).isEqualTo("PENDING");
		assertThat(notifications.byId(later).attempts()).isZero();
	}

}
