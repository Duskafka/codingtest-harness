package com.duskafka.studybot.notification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.NOV_28;
import static com.duskafka.studybot.progress.StudyDayRows.SEOUL;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.notification.NotificationRows.NotificationRow;
import com.duskafka.studybot.notification.reminder.ReminderGenerator;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 마감 전 알림 생성 (S13 만들 것 2, 5.8, pseudocode '마감 전 알림', T14·T15).
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 알림 오프셋 120분, 1기 11-01~11-28 ({@link StudyDayRows#standardStudy}).
 * 그래서 11/20의 알림 시각은 11/20 22:00, 마감은 11/21 00:00이다. 11/20은 금요일이다. 테스트는 스터디 하나씩
 * {@link ReminderGenerator#remindStudy}로 돌린다 (공유 DB의 다른 스터디를 건드리지 않음).
 */
@IntegrationTest
class ReminderGenerationIntegrationTest {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private ReminderGenerator generator;

	private StudyDayRows rows;

	private NotificationRows notifications;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		notifications = new NotificationRows(jdbc);
	}

	@Test
	@DisplayName("T14: 알림 대상에서 달성한 멤버, 면제권을 쓴 멤버, 판정 대상이 아닌 멤버(가입한 날)가 빠지고, 대상이 있으면 방장에게 공유 유도를 만든다")
	void t14TargetsExcludeDoneExemptedAndNotJudgedMembers() {
		long alice = rows.newUser("s13-alice");
		long bob = rows.newUser("s13-bob");
		long carol = rows.newUser("s13-carol");
		long dave = rows.newUser("s13-dave");
		long owner = rows.newUser("s13-owner");
		TestStudy study = rows.standardStudy(alice);
		long studyId = study.studyId();
		rows.join(studyId, bob, NOV_1);
		rows.join(studyId, carol, NOV_1);
		// 11/20 낮에 가입: 다음 날부터 판정한다 (R14). 그날은 판정 대상이 아니다.
		rows.join(studyId, dave, seoul(NOV_20, 9, 0), NOV_21);
		rows.joinAsOwner(studyId, owner, NOV_1);
		rows.putMemberDay(studyId, bob, NOV_20, 1, null, null);
		rows.putMemberDay(studyId, carol, NOV_20, 0, null, seoul(NOV_20, 12, 0));
		rows.putMemberDay(studyId, owner, NOV_20, 1, null, null);
		clock.setInstant(seoul(NOV_20, 22, 0));

		generator.remindStudy(studyId);

		assertThat(notifications.ofStudy(studyId, "REMINDER")).extracting(NotificationRow::userId).containsExactly(alice);
		assertThat(notifications.ofStudy(studyId, "SHARE_PROMPT")).extracting(NotificationRow::userId)
			.containsExactly(owner);
		assertThat(notifications.lastRemindedDay(studyId)).isEqualTo(NOV_20);
	}

	@Test
	@DisplayName("T14: 쉬는 날에는 알림을 만들지 않고 진행 지점만 올린다")
	void t14RestDayHasNoTargets() {
		long alice = rows.newUser("s13-rest");
		long owner = rows.newUser("s13-rest-owner");
		TestStudy study = rows.standardStudy(alice, SEOUL, LocalTime.MIDNIGHT, 30, "FRIDAY");
		rows.joinAsOwner(study.studyId(), owner, NOV_1);
		clock.setInstant(seoul(NOV_20, 22, 0));

		generator.remindStudy(study.studyId());

		assertThat(notifications.ofStudy(study.studyId())).isEmpty();
		assertThat(notifications.lastRemindedDay(study.studyId())).isEqualTo(NOV_20);
	}

	@Test
	@DisplayName("T14: 기수 밖인 날(1기가 끝난 다음 날, 다음 기수 행 없음)은 판정 대상이 없어 알림을 만들지 않는다")
	void t14OutsideSeasonHasNoTargets() {
		long alice = rows.newUser("s13-outside");
		TestStudy study = rows.standardStudy(alice);
		LocalDate nov29 = NOV_28.plusDays(1);
		clock.setInstant(seoul(nov29, 22, 0));

		generator.remindStudy(study.studyId());

		assertThat(notifications.ofStudy(study.studyId())).isEmpty();
		assertThat(notifications.lastRemindedDay(study.studyId())).isEqualTo(nov29);
	}

	@Test
	@DisplayName("T14: 그날 전에 탈퇴한 멤버는 알림 대상이 아니다")
	void t14MemberWhoLeftBeforeTheDayIsNotTargeted() {
		long alice = rows.newUser("s13-stay");
		long bob = rows.newUser("s13-left");
		TestStudy study = rows.standardStudy(alice);
		long bobMembership = rows.join(study.studyId(), bob, NOV_1);
		rows.leave(bobMembership, seoul(NOV_20.minusDays(1), 12, 0));
		clock.setInstant(seoul(NOV_20, 22, 0));

		generator.remindStudy(study.studyId());

		assertThat(notifications.ofStudy(study.studyId(), "REMINDER")).extracting(NotificationRow::userId)
			.containsExactly(alice);
	}

	@Test
	@DisplayName("T15: 알림 생성 스케줄러가 두 번 돌아도 알림은 1개다. 진행 지점을 지워 다시 돌려도 dedupe_key가 막는다")
	void t15RunningTwiceCreatesOneNotification() {
		long alice = rows.newUser("s13-twice");
		long owner = rows.newUser("s13-twice-owner");
		TestStudy study = rows.standardStudy(alice);
		rows.joinAsOwner(study.studyId(), owner, NOV_1);
		clock.setInstant(seoul(NOV_20, 22, 0));

		generator.remindStudy(study.studyId());
		generator.remindStudy(study.studyId());
		notifications.setLastRemindedDay(study.studyId(), null);
		generator.remindStudy(study.studyId());

		assertThat(notifications.ofStudy(study.studyId(), "REMINDER")).extracting(NotificationRow::userId)
			.containsExactlyInAnyOrder(alice, owner);
		assertThat(notifications.ofStudy(study.studyId(), "SHARE_PROMPT")).hasSize(1);
	}

	@Test
	@DisplayName("알림 시각 전에는 만들지 않고 진행 지점도 그대로다")
	void nothingBeforeReminderTime() {
		long alice = rows.newUser("s13-early");
		TestStudy study = rows.standardStudy(alice);
		clock.setInstant(seoul(NOV_20, 21, 59));

		generator.remindStudy(study.studyId());

		assertThat(notifications.ofStudy(study.studyId())).isEmpty();
		assertThat(notifications.lastRemindedDay(study.studyId())).isNull();
	}

	@Test
	@DisplayName("대상이 없으면 공유 유도도 만들지 않고 진행 지점만 올린다")
	void noSharePromptWithoutTargets() {
		long alice = rows.newUser("s13-done");
		long owner = rows.newUser("s13-done-owner");
		TestStudy study = rows.standardStudy(alice);
		rows.joinAsOwner(study.studyId(), owner, NOV_1);
		rows.putMemberDay(study.studyId(), alice, NOV_20, 1, null, null);
		rows.putMemberDay(study.studyId(), owner, NOV_20, 1, null, null);
		clock.setInstant(seoul(NOV_20, 22, 30));

		generator.remindStudy(study.studyId());

		assertThat(notifications.ofStudy(study.studyId())).isEmpty();
		assertThat(notifications.lastRemindedDay(study.studyId())).isEqualTo(NOV_20);
	}

	@Test
	@DisplayName("알림 행: 웹 푸시, 발송 대기, 만료 = 그날 마감, dedupe_key는 5.8 형식, payload에 남은 문제 수")
	void notificationRowsCarryDeadlineAndDedupeKey() {
		long alice = rows.newUser("s13-row");
		long owner = rows.newUser("s13-row-owner");
		TestStudy study = rows.standardStudy(alice);
		long studyId = study.studyId();
		rows.joinAsOwner(studyId, owner, NOV_1);
		rows.putMemberDay(studyId, owner, NOV_20, 1, null, null);
		clock.setInstant(seoul(NOV_20, 22, 5));

		generator.remindStudy(studyId);

		NotificationRow reminder = notifications.ofStudy(studyId, "REMINDER").getFirst();
		assertThat(reminder.channel()).isEqualTo("WEB_PUSH");
		assertThat(reminder.status()).isEqualTo("PENDING");
		assertThat(reminder.studyDay()).isEqualTo(NOV_20);
		assertThat(reminder.dedupeKey()).isEqualTo("REMINDER:" + studyId + ":" + alice + ":2026-11-20");
		assertThat(reminder.expiresAt()).isEqualTo(seoul(NOV_21, 0, 0));
		assertThat(reminder.nextAttemptAt()).isEqualTo(seoul(NOV_20, 22, 5));
		assertThat(reminder.attempts()).isZero();
		JsonNode payload = JSON.readTree(reminder.payload());
		assertThat(payload.path("type").asString()).isEqualTo("REMINDER");
		assertThat(payload.path("body").asString()).contains("1문제");
		assertThat(payload.path("url").asString()).isEqualTo("/studies/" + studyId + "/today");

		NotificationRow share = notifications.ofStudy(studyId, "SHARE_PROMPT").getFirst();
		assertThat(share.userId()).isEqualTo(owner);
		assertThat(share.dedupeKey()).isEqualTo("SHARE_PROMPT:" + studyId + ":2026-11-20");
		assertThat(share.expiresAt()).isEqualTo(seoul(NOV_21, 0, 0));
		assertThat(JSON.readTree(share.payload()).path("type").asString()).isEqualTo("SHARE_PROMPT");
	}

	@Test
	@DisplayName("서버가 알림 시각부터 마감 뒤까지 꺼져 있었으면 켜질 때 그날 알림을 만들지 않는다. 다음 날 알림은 그날 알림 시각에 만든다")
	void serverDownPastDeadlineSkipsThatDay() {
		long alice = rows.newUser("s13-down");
		TestStudy study = rows.standardStudy(alice);
		notifications.setLastRemindedDay(study.studyId(), NOV_20.minusDays(1));
		clock.setInstant(seoul(NOV_21, 0, 10));

		generator.remindStudy(study.studyId());

		assertThat(notifications.ofStudy(study.studyId())).isEmpty();
		assertThat(notifications.lastRemindedDay(study.studyId())).isEqualTo(NOV_20.minusDays(1));

		clock.setInstant(seoul(NOV_21, 22, 0));
		generator.remindStudy(study.studyId());

		assertThat(notifications.ofStudy(study.studyId(), "REMINDER")).extracting(NotificationRow::studyDay)
			.containsExactly(NOV_21);
	}

	@Test
	@DisplayName("오늘 바꾼 알림 오프셋은 다음 스터디 일부터: 그날은 그날 규칙의 오프셋으로 만든다 (R2)")
	void changedOffsetAppliesFromNextStudyDay() {
		long alice = rows.newUser("s13-offset");
		long studyId = rows.newStudy(SEOUL);
		rows.addRule(studyId, NOV_1, 1, LocalTime.MIDNIGHT, 30, "");
		long changed = rows.addRule(studyId, NOV_21, 1, LocalTime.MIDNIGHT, 30, "");
		notifications.setReminderOffset(changed, 60);
		rows.addSeason(studyId, 1, NOV_1, NOV_28);
		rows.join(studyId, alice, NOV_1);

		clock.setInstant(seoul(NOV_20, 22, 0));
		generator.remindStudy(studyId);
		clock.setInstant(seoul(NOV_21, 22, 30));
		generator.remindStudy(studyId);

		assertThat(notifications.ofStudy(studyId, "REMINDER")).extracting(NotificationRow::studyDay)
			.containsExactly(NOV_20);

		clock.setInstant(seoul(NOV_21, 23, 0));
		generator.remindStudy(studyId);

		assertThat(notifications.ofStudy(studyId, "REMINDER")).extracting(NotificationRow::studyDay)
			.containsExactly(NOV_20, NOV_21);
	}

}
