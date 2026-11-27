package com.duskafka.studybot.progress;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.LocalDate;
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
 * 현황의 P1 정산 표시: 이번 기수의 실패 횟수 × 벌금액 (S12 만들 것 7, 2.3 '미인증 횟수 × 금액 표시', 알려진 충돌 9번).
 * 벌금 방식이 '없음'이면 보이지 않는다. 모든 멤버에게 멤버마다 보인다 (사람이 정함, 2026-11-27 "모든 멤버에게 멤버마다").
 *
 * <p>기본 스터디: 1기 11-01~11-28 ({@link StudyDayRows#standardStudy}). 지금은 11-20 12:00이다.
 */
@IntegrationTest
class StudyDayPenaltyIntegrationTest {

	private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

	private static final LocalDate NOV_17 = LocalDate.of(2026, 11, 17);

	private static final LocalDate NOV_18 = LocalDate.of(2026, 11, 18);

	private static final LocalDate NOV_19 = LocalDate.of(2026, 11, 19);

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	private StudyDayRows rows;

	private TestApi api;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		api = new TestApi(mvc, jdbc);
		clock.setInstant(seoul(NOV_20, 12, 0));
	}

	@Test
	@DisplayName("벌금 방식이면 이번 기수의 실패 횟수와 실패 × 벌금액을 멤버마다 모두에게 보여 준다")
	void fineShowsFailuresTimesAmountToEveryone() {
		Fixture fixture = fixture("s12-fine", "FINE", 1000);

		JsonNode today = TestApi.json(today(fixture.bob, fixture.study.studyId()));

		assertThat(today.get("penaltyType").asString()).isEqualTo("FINE");
		assertThat(today.get("fineAmount").asInt()).isEqualTo(1000);
		JsonNode alice = member(today, fixture.alice);
		assertThat(alice.get("failCount").asInt()).as("기수 밖 10-31의 실패는 세지 않음").isEqualTo(2);
		assertThat(alice.get("fineTotal").asInt()).isEqualTo(2000);
		JsonNode bob = member(today, fixture.bob);
		assertThat(bob.get("failCount").asInt()).isZero();
		assertThat(bob.get("fineTotal").asInt()).isZero();
	}

	@Test
	@DisplayName("보증금 환급 방식도 같은 계산(보증금에서 깎일 금액)으로 보여 준다")
	void depositRefundShowsSameCalculation() {
		Fixture fixture = fixture("s12-deposit", "DEPOSIT_REFUND", 500);

		JsonNode today = TestApi.json(today(fixture.alice, fixture.study.studyId()));

		assertThat(today.get("penaltyType").asString()).isEqualTo("DEPOSIT_REFUND");
		assertThat(today.get("fineAmount").asInt()).isEqualTo(500);
		assertThat(member(today, fixture.alice).get("fineTotal").asInt()).isEqualTo(1000);
	}

	@Test
	@DisplayName("벌금 방식이 '없음'이면 정산 표시는 비어 있다")
	void noneHidesPenalty() {
		Fixture fixture = fixture("s12-none", "NONE", 1000);

		JsonNode today = TestApi.json(today(fixture.alice, fixture.study.studyId()));

		assertThat(today.get("penaltyType").asString()).isEqualTo("NONE");
		assertThat(today.get("fineAmount").isNull()).isTrue();
		assertThat(member(today, fixture.alice).get("failCount").isNull()).isTrue();
		assertThat(member(today, fixture.alice).get("fineTotal").isNull()).isTrue();
	}

	@Test
	@DisplayName("기수 밖인 날의 상세에는 멤버의 정산 표시가 비어 있다")
	void outsideSeasonHidesMemberPenalty() {
		Fixture fixture = fixture("s12-outside", "FINE", 1000);

		MvcTestResult result = api.get(fixture.alice, "/api/studies/{studyId}/days/{date}", fixture.study.studyId(),
				OCT_31);

		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode alice = member(TestApi.json(result), fixture.alice);
		assertThat(alice.get("failCount").isNull()).isTrue();
		assertThat(alice.get("fineTotal").isNull()).isTrue();
	}

	private Fixture fixture(String prefix, String penaltyType, int fineAmount) {
		long alice = rows.newUser(prefix + "-a");
		long bob = rows.newUser(prefix + "-b");
		TestStudy study = rows.standardStudy(alice);
		rows.join(study.studyId(), bob, NOV_1);
		jdbc.sql("update studybot.study_rules set penalty_type = :type, fine_amount = :amount where study_id = :studyId")
			.param("type", penaltyType)
			.param("amount", fineAmount)
			.param("studyId", study.studyId())
			.update();
		rows.putMemberDay(study.studyId(), alice, OCT_31, 0, "FAIL", null);
		rows.putMemberDay(study.studyId(), alice, NOV_17, 1, "SUCCESS", null);
		rows.putMemberDay(study.studyId(), alice, NOV_18, 0, "FAIL", null);
		rows.putMemberDay(study.studyId(), alice, NOV_19, 0, "FAIL", null);
		rows.putMemberDay(study.studyId(), bob, NOV_19, 1, "SUCCESS", null);
		return new Fixture(alice, bob, study);
	}

	private MvcTestResult today(long userId, long studyId) {
		MvcTestResult result = api.get(userId, "/api/studies/{studyId}/today", studyId);
		assertThat(result).hasStatus(HttpStatus.OK);
		return result;
	}

	private static JsonNode member(JsonNode status, long userId) {
		for (JsonNode member : status.get("members")) {
			if (member.get("userId").asLong() == userId) {
				return member;
			}
		}
		throw new AssertionError("현황에 멤버가 없어요: " + userId);
	}

	private record Fixture(long alice, long bob, TestStudy study) {
	}

}
