package com.duskafka.studybot.notification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.NOV_28;
import static com.duskafka.studybot.progress.StudyDayRows.SEOUL;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.judgment.DailyJudgmentBatch;
import com.duskafka.studybot.judgment.JudgmentSummary;
import com.duskafka.studybot.notification.DiscordStub.Webhook;
import com.duskafka.studybot.notification.NotificationRows.NotificationRow;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 판정 결과 알림 {@code DAILY_RESULT} (S14 만들 것 5, 수용 기준 '하루에 하나'·'이벤트가 사라져도', 결정 제안서 010 '검증 방법').
 *
 * <p>판정 배치({@link DailyJudgmentBatch#judgeStudy})가 진행 지점을 올리는 트랜잭션에서 study가 이벤트를 발행하고, notification이 같은
 * 트랜잭션에서 outbox 행을 넣는다 (사람이 정함, 2026-11-28 "같은 트랜잭션 리스너").
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 1기 11-01~11-28. 11-20의 판정 시각은 11-21 00:40이다. 진행 지점을
 * 11-19로 두고 11-21 00:40에 돌리면 11-20 하루만 판정한다. 2026-11-20은 금요일이다.
 */
@IntegrationTest
class DailyResultIntegrationTest {

	private static final LocalDate NOV_17 = LocalDate.of(2026, 11, 17);

	private static final LocalDate NOV_19 = LocalDate.of(2026, 11, 19);

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private static final LocalDate NOV_22 = LocalDate.of(2026, 11, 22);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private DailyJudgmentBatch batch;

	private StudyDayRows rows;

	private NotificationRows notifications;

	private TestApi api;

	private long studyId;

	private long owner;

	private long alice;

	private long bob;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		notifications = new NotificationRows(jdbc);
		api = new TestApi(mvc, jdbc);
		alice = rows.newUser("s14-result-alice");
		bob = rows.newUser("s14-result-bob");
		TestStudy study = rows.standardStudy(alice);
		studyId = study.studyId();
		rows.join(studyId, bob, NOV_1);
		owner = notifications.ownerOf(studyId);
		rows.joinAsOwner(studyId, owner, NOV_1);
		rows.putMemberDay(studyId, alice, NOV_20, 1, null, null);
		setLastJudgedDay(studyId, NOV_19);
		clock.setInstant(seoul(NOV_21, 0, 40));
	}

	private void setLastJudgedDay(long study, @Nullable LocalDate day) {
		jdbc.sql("update studybot.studies set last_judged_day = :day where id = :id")
			.param("day", day)
			.param("id", study)
			.update();
	}

	private @Nullable LocalDate lastJudgedDay(long study) {
		return jdbc.sql("select last_judged_day from studybot.studies where id = :id")
			.param("id", study)
			.query((rs, rowNum) -> rs.getObject("last_judged_day", LocalDate.class))
			.single();
	}

	private @Nullable String result(long study, long userId, LocalDate day) {
		return jdbc.sql("select result from studybot.member_days where study_id = :s and user_id = :u and study_day = :d")
			.param("s", study)
			.param("u", userId)
			.param("d", day)
			.query(String.class)
			.optional()
			.orElse(null);
	}

	@Test
	@DisplayName("디스코드가 없으면 판정이 끝난 날의 결과를 방장 웹 푸시로 하나 만든다. payload는 숫자만이고 이름이 없다")
	void webPushToOwnerWithoutDiscord() {
		batch.judgeStudy(studyId);

		assertThat(lastJudgedDay(studyId)).isEqualTo(NOV_20);
		NotificationRow row = notifications.ofStudy(studyId, "DAILY_RESULT").getFirst();
		assertThat(row.channel()).isEqualTo("WEB_PUSH");
		assertThat(row.userId()).isEqualTo(owner);
		assertThat(row.studyDay()).isEqualTo(NOV_20);
		assertThat(row.dedupeKey()).isEqualTo("DAILY_RESULT:" + studyId + ":" + NOV_20);
		assertThat(row.status()).isEqualTo("PENDING");
		assertThat(row.expiresAt()).as("판정한 날 다음 스터디 일이 끝날 때").isEqualTo(seoul(NOV_22, 0, 0));
		JsonNode payload = JSON.readTree(row.payload());
		assertThat(payload.path("type").asString()).isEqualTo("DAILY_RESULT");
		assertThat(payload.path("title").asString()).isEqualTo("S09 스터디");
		assertThat(payload.path("body").asString()).isEqualTo("11/20 결과: 성공 1명 · 실패 2명");
		assertThat(payload.path("url").asString()).isEqualTo("/studies/" + studyId + "/days/2026-11-20");
		assertThat(row.payload()).doesNotContain(notifications.loginOf(bob)).doesNotContain(notifications.loginOf(alice));
	}

	@Test
	@DisplayName("디스코드가 켜져 있으면 결과 알림은 디스코드 채널 행 하나다 (방장 웹 푸시는 만들지 않음)")
	void discordWhenRegistered() {
		Webhook webhook = DiscordStub.newWebhook();
		assertThat(api.putJson(owner, "/api/studies/{id}/channels/discord",
				"{\"webhookUrl\":\"" + webhook.officialUrl() + "\"}", studyId))
			.hasStatusOk();

		batch.judgeStudy(studyId);

		assertThat(notifications.ofStudy(studyId, "DAILY_RESULT")).singleElement().satisfies(row -> {
			assertThat(row.channel()).isEqualTo("DISCORD");
			assertThat(row.userId()).isNull();
			assertThat(row.studyDay()).isEqualTo(NOV_20);
		});
	}

	@Test
	@DisplayName("판정 배치를 두 번 돌려도(진행 지점을 되돌려 같은 날을 다시 올려도) DAILY_RESULT는 하루에 하나다")
	void oncePerDayEvenWhenJudgedTwice() {
		batch.judgeStudy(studyId);
		batch.judgeStudy(studyId);
		setLastJudgedDay(studyId, NOV_19);
		batch.judgeStudy(studyId);

		assertThat(lastJudgedDay(studyId)).isEqualTo(NOV_20);
		assertThat(notifications.ofStudy(studyId, "DAILY_RESULT")).hasSize(1);
	}

	@Test
	@DisplayName("판정 뒤 결과 알림을 넣지 못하면(서버가 죽음과 같음) 진행 지점도 올라가지 않고, 다음 실행이 결국 결과 알림을 만든다")
	void eventuallyCreatedAfterFailure() {
		String trigger = "s14_fail_daily_result_" + studyId;
		jdbc.sql("""
				create function studybot.%1$s() returns trigger language plpgsql as $f$
				begin
					if new.type = 'DAILY_RESULT' and new.study_id = %2$d then
						raise exception 'S14 테스트가 일부러 낸 오류';
					end if;
					return new;
				end $f$
				""".formatted(trigger, studyId)).update();
		jdbc.sql("create trigger %1$s before insert on studybot.notifications for each row execute function studybot.%1$s()"
			.formatted(trigger)).update();
		JudgmentSummary failed;
		try {
			failed = batch.judgeStudy(studyId);
		}
		finally {
			jdbc.sql("drop trigger %1$s on studybot.notifications".formatted(trigger)).update();
			jdbc.sql("drop function studybot.%1$s()".formatted(trigger)).update();
		}

		assertThat(failed.errors()).isEqualTo(1);
		assertThat(result(studyId, alice, NOV_20)).as("멤버 결과는 이미 저장").isEqualTo("SUCCESS");
		assertThat(lastJudgedDay(studyId)).isEqualTo(NOV_19);
		assertThat(notifications.ofStudy(studyId, "DAILY_RESULT")).isEmpty();

		batch.judgeStudy(studyId);

		assertThat(lastJudgedDay(studyId)).isEqualTo(NOV_20);
		assertThat(notifications.ofStudy(studyId, "DAILY_RESULT")).hasSize(1);
	}

	@Test
	@DisplayName("한 스터디의 결과 알림이 실패해도 판정 배치 전체(run)는 다른 스터디를 계속 판정한다")
	void failureDoesNotStopOtherStudies() {
		long carol = rows.newUser("s14-result-carol");
		TestStudy other = rows.standardStudy(carol);
		setLastJudgedDay(other.studyId(), NOV_19);
		String trigger = "s14_fail_daily_result_run_" + studyId;
		jdbc.sql("""
				create function studybot.%1$s() returns trigger language plpgsql as $f$
				begin
					if new.type = 'DAILY_RESULT' and new.study_id = %2$d then
						raise exception 'S14 테스트가 일부러 낸 오류';
					end if;
					return new;
				end $f$
				""".formatted(trigger, studyId)).update();
		jdbc.sql("create trigger %1$s before insert on studybot.notifications for each row execute function studybot.%1$s()"
			.formatted(trigger)).update();
		try {
			batch.judgeStudy(studyId);
			batch.judgeStudy(other.studyId());
		}
		finally {
			jdbc.sql("drop trigger %1$s on studybot.notifications".formatted(trigger)).update();
			jdbc.sql("drop function studybot.%1$s()".formatted(trigger)).update();
		}

		assertThat(lastJudgedDay(studyId)).isEqualTo(NOV_19);
		assertThat(lastJudgedDay(other.studyId())).isEqualTo(NOV_20);
	}

	@Test
	@DisplayName("혼자 쓰기 스터디의 결과 알림은 방장 웹 푸시다")
	void soloStudyGoesToOwner() {
		long solo = rows.newStudy(SEOUL);
		jdbc.sql("update studybot.studies set mode = 'SOLO' where id = :id").param("id", solo).update();
		rows.addRule(solo, NOV_1, 1, LocalTime.MIDNIGHT, 30, "");
		rows.addSeason(solo, 1, NOV_1, NOV_28);
		long soloOwner = notifications.ownerOf(solo);
		rows.joinAsOwner(solo, soloOwner, NOV_1);
		setLastJudgedDay(solo, NOV_19);

		batch.judgeStudy(solo);

		assertThat(notifications.ofStudy(solo, "DAILY_RESULT")).singleElement().satisfies(row -> {
			assertThat(row.channel()).isEqualTo("WEB_PUSH");
			assertThat(row.userId()).isEqualTo(soloOwner);
			assertThat(JSON.readTree(row.payload()).path("body").asString()).isEqualTo("11/20 결과: 실패 1명");
		});
	}

	@Test
	@DisplayName("쉬는 날처럼 성공·실패·면제권 결과가 하나도 없는 날은 결과 알림을 만들지 않는다 (진행 지점은 올라감)")
	void restDayCreatesNothing() {
		long dave = rows.newUser("s14-result-rest");
		TestStudy rest = rows.standardStudy(dave, SEOUL, LocalTime.MIDNIGHT, 30, "FRIDAY");
		setLastJudgedDay(rest.studyId(), NOV_19);

		batch.judgeStudy(rest.studyId());

		assertThat(lastJudgedDay(rest.studyId())).isEqualTo(NOV_20);
		assertThat(result(rest.studyId(), dave, NOV_20)).isEqualTo("EXEMPT_REST");
		assertThat(notifications.ofStudy(rest.studyId(), "DAILY_RESULT")).isEmpty();
	}

	@Test
	@DisplayName("오래 꺼졌다 따라잡은 날 중 다음 스터디 일까지 지난 날은 결과 알림을 만들지 않는다")
	void oldCatchUpDaysAreSkipped() {
		setLastJudgedDay(studyId, NOV_17);

		batch.judgeStudy(studyId);

		assertThat(lastJudgedDay(studyId)).isEqualTo(NOV_20);
		assertThat(notifications.ofStudy(studyId, "DAILY_RESULT")).extracting(NotificationRow::studyDay)
			.containsExactly(NOV_20);
	}

	@Test
	@DisplayName("면제권 결과는 '면제'로 센다")
	void exemptionCounted() {
		rows.putMemberDay(studyId, bob, NOV_20, 0, null, seoul(NOV_20, 10, 0));

		batch.judgeStudy(studyId);

		NotificationRow row = notifications.ofStudy(studyId, "DAILY_RESULT").getFirst();
		assertThat(JSON.readTree(row.payload()).path("body").asString()).isEqualTo("11/20 결과: 성공 1명 · 실패 1명 · 면제 1명");
	}

}
