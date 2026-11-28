package com.duskafka.studybot.notification;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.SEOUL;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/**
 * 공유 카드 {@code GET /api/studies/{id}/share-card?type=today} (S14 만들 것 6, 수용 기준 '벌금 금액이 기본으로 들어가지 않는다'·T19,
 * 5.9 '카드 예시'·'텍스트 복사').
 *
 * <p>기본 스터디: 서울, 기준 시각 00:00, 목표 1문제, 1기 11-01~11-28, 이름 'S09 스터디'. 지금은 11/20 21:00이다.
 */
@IntegrationTest
class ShareCardIntegrationTest {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	private StudyDayRows rows;

	private NotificationRows notifications;

	private TestApi api;

	private long studyId;

	private long alice;

	private long bob;

	private TestStudy study;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		notifications = new NotificationRows(jdbc);
		api = new TestApi(mvc, jdbc);
		alice = rows.newUser("s14-card-alice");
		bob = rows.newUser("s14-card-bob");
		study = rows.standardStudy(alice);
		studyId = study.studyId();
		rows.join(studyId, bob, NOV_1);
		clock.setInstant(seoul(NOV_20, 21, 0));
	}

	private MvcTestResult card(long userId, long study) {
		return api.getWithParams(userId, "/api/studies/" + study + "/share-card", "type", "today");
	}

	@Test
	@DisplayName("오늘 카드: 제목, 완료·남음 수와 남은 멤버 이름과 마감, 현황 링크(절대 주소), 텍스트 복사용 세 줄, 수를 준다")
	void todayCard() {
		rows.putMemberDay(studyId, alice, NOV_20, 1, null, null);

		MvcTestResult result = card(alice, studyId);

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode json = TestApi.json(result);
		String url = "https://studybot.example.invalid/studies/" + studyId + "/today";
		String title = "[S09 스터디] 11/20 인증 현황";
		String summary = "완료 1명 · 남음 1명 (" + notifications.loginOf(bob) + ") · 마감 24:00";
		assertThat(json.path("type").asString()).isEqualTo("today");
		assertThat(json.path("studyId").asLong()).isEqualTo(studyId);
		assertThat(json.path("studyDay").asString()).isEqualTo("2026-11-20");
		assertThat(json.path("title").asString()).isEqualTo(title);
		assertThat(json.path("summary").asString()).isEqualTo(summary);
		assertThat(json.path("url").asString()).isEqualTo(url);
		assertThat(json.path("text").asString()).isEqualTo(title + "\n" + summary + "\n현황 보기: " + url);
		assertThat(json.path("counts").path("done").asInt()).isEqualTo(1);
		assertThat(json.path("counts").path("remaining").asInt()).isEqualTo(1);
		assertThat(json.path("counts").path("exempt").asInt()).isZero();
	}

	@Test
	@DisplayName("벌금 방식이 있어도 공유 카드에 벌금 금액이 들어가지 않는다 (5.9, 사람이 정함 2026-11-28 'S14는 늘 뺌')")
	void noFineAmount() {
		jdbc.sql("update studybot.study_rules set penalty_type = 'FINE', fine_amount = 5432 where study_id = :id")
			.param("id", studyId)
			.update();
		rows.markJudged(studyId, bob, LocalDate.of(2026, 11, 19), "FAIL");

		MvcTestResult result = card(alice, studyId);

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode json = TestApi.json(result);
		assertThat(json.toString()).doesNotContain("5432").doesNotContain("원").doesNotContainIgnoringCase("fine");
		assertThat(json.has("fineAmount")).isFalse();
		assertThat(json.has("penaltyType")).isFalse();
	}

	@Test
	@DisplayName("면제권을 쓴 멤버는 '면제'로 세고 남은 이름에 넣지 않는다. 모두 끝났으면 '남음 0명'이고 이름 괄호가 없다")
	void exemptAndAllDone() {
		rows.putMemberDay(studyId, alice, NOV_20, 1, null, null);
		rows.putMemberDay(studyId, bob, NOV_20, 0, null, seoul(NOV_20, 9, 0));

		JsonNode json = TestApi.json(card(bob, studyId));

		assertThat(json.path("summary").asString()).isEqualTo("완료 1명 · 남음 0명 · 면제 1명 · 마감 24:00");
		assertThat(json.path("counts").path("exempt").asInt()).isEqualTo(1);
	}

	@Test
	@DisplayName("남은 멤버가 10명보다 많으면 앞 10명만 쓰고 '외 N명'을 붙인다")
	void manyRemainingNamesAreCut() {
		List<String> logins = new ArrayList<>();
		logins.add(notifications.loginOf(alice));
		logins.add(notifications.loginOf(bob));
		for (int i = 0; i < 10; i++) {
			long extra = rows.newUser("s14-card-x" + i);
			rows.join(studyId, extra, NOV_1);
			logins.add(notifications.loginOf(extra));
		}

		String summary = TestApi.json(card(alice, studyId)).path("summary").asString();

		assertThat(summary).startsWith("완료 0명 · 남음 12명 (" + String.join(", ", logins.subList(0, 10)) + " 외 2명)");
		assertThat(summary).doesNotContain(logins.get(10)).doesNotContain(logins.get(11));
	}

	@Test
	@DisplayName("쉬는 날은 요약이 쉬는 날 안내다")
	void restDayCard() {
		long dave = rows.newUser("s14-card-rest");
		TestStudy rest = rows.standardStudy(dave, SEOUL, LocalTime.MIDNIGHT, 30, "FRIDAY");

		JsonNode json = TestApi.json(card(dave, rest.studyId()));

		assertThat(json.path("summary").asString()).isEqualTo("오늘은 쉬는 날이에요");
		assertThat(json.path("counts").path("remaining").asInt()).isZero();
	}

	@Test
	@DisplayName("type이 today가 아니거나 없으면 400이다 (주간 카드는 P2)")
	void unknownTypeIsBadRequest() {
		assertThat(api.getWithParams(alice, "/api/studies/" + studyId + "/share-card", "type", "weekly"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(api.get(alice, "/api/studies/{id}/share-card", studyId)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("혼자 쓰기 스터디는 공유 카드를 409(SOLO_STUDY_NO_SHARING)로 거절한다 (R15, 사람이 정함 2026-11-28)")
	void soloStudyRejected() {
		notifications.setStudyMode(studyId, "SOLO");

		MvcTestResult result = card(alice, studyId);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(TestApi.json(result).path("code").asString()).isEqualTo("SOLO_STUDY_NO_SHARING");
	}

	@Test
	@DisplayName("T19: 멤버가 아니거나 탈퇴했거나 다른 스터디 ID면 404, 로그인하지 않으면 401이다")
	void t19OthersCannotReadCard() {
		long outsider = rows.newUser("s14-card-outsider");
		long otherMember = rows.newUser("s14-card-other");
		rows.standardStudy(otherMember);
		long leaver = rows.newUser("s14-card-leaver");
		long leaverMembership = rows.join(studyId, leaver, NOV_1);
		rows.leave(leaverMembership, seoul(NOV_20, 8, 0));

		assertThat(card(outsider, studyId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(card(otherMember, studyId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(card(leaver, studyId)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(card(alice, Long.MAX_VALUE)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(api.getAnonymous("/api/studies/{id}/share-card?type=today", studyId))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
