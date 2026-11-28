package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.notification.NotificationRows;
import com.duskafka.studybot.notification.NotificationRows.NotificationRow;
import com.duskafka.studybot.notification.dispatch.NotificationDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 규칙 변경 알림 {@code RULE_CHANGED} (S13 만들 것 4, R2, 5.8 '알림 종류', 알려진 충돌 5번).
 *
 * <p>study → notification 화살표가 없어서 study는 규칙 변경 트랜잭션 안에서 이벤트를 발행하고, notification이 같은 트랜잭션에서 알림 행을
 * 넣는다 (사람이 정함, 2026-11-27 S13 세션 "Listener in the same transaction (Recommended)"). 그래서 규칙 변경이 롤백되면 알림도
 * 없다.
 */
class RuleChangeNotificationIntegrationTest extends StudyTestSupport {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** 11/20 12:00 KST: 스터디를 만드는 시각. 시작일 11/21. */
	private static final String CREATED_AT = "2026-11-20T03:00:00Z";

	/** 11/25 12:00 KST: 1기 중간. */
	private static final Instant MID_SEASON = Instant.parse("2026-11-25T03:00:00Z");

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private NotificationDispatcher dispatcher;

	private NotificationRows notifications;

	private TestUser owner;

	private TestUser member;

	private long studyId;

	@BeforeEach
	void setUp() {
		notifications = new NotificationRows(jdbc);
		setClock(CREATED_AT);
		owner = newUser("rule-notify-owner");
		member = newUser("rule-notify-member");
		studyId = createStudy(owner, "GROUP");
		join(owner, studyId, member);
		clock.setInstant(MID_SEASON);
	}

	@Test
	@DisplayName("규칙이 바뀌면 바꾼 방장을 뺀 멤버에게 RULE_CHANGED를 만든다. dedupe_key는 RULE:{스터디}:{마지막 버전}:{사용자}다")
	void ruleChangeNotifiesMembersExceptActor() {
		assertThat(putRules(rules(Map.of("dailyGoal", 2)))).hasStatus(HttpStatus.OK);

		List<NotificationRow> rows = notifications.ofStudy(studyId, "RULE_CHANGED");
		assertThat(rows).extracting(NotificationRow::userId).containsExactly(member.id());
		NotificationRow row = rows.getFirst();
		assertThat(row.dedupeKey()).isEqualTo("RULE:" + studyId + ":" + latestRuleId() + ":" + member.id());
		assertThat(row.channel()).isEqualTo("WEB_PUSH");
		assertThat(row.status()).isEqualTo("PENDING");
		assertThat(row.studyDay()).isNull();
		assertThat(row.expiresAt()).isEqualTo(MID_SEASON.plus(Duration.ofHours(24)));
		JsonNode payload = JSON.readTree(row.payload());
		assertThat(payload.path("type").asString()).isEqualTo("RULE_CHANGED");
		assertThat(payload.path("url").asString()).isEqualTo("/studies/" + studyId);
		assertThat(payload.path("body").asString()).contains("11월 26일");
	}

	@Test
	@DisplayName("지금 규칙과 같게 보내 새 버전이 생기지 않으면 알림도 없다")
	void noNewVersionNoNotification() {
		assertThat(putRules(rules(Map.of()))).hasStatus(HttpStatus.OK);

		assertThat(notifications.ofStudy(studyId, "RULE_CHANGED")).isEmpty();
	}

	@Test
	@DisplayName("예정된 변경을 취소하면(지금 규칙과 같게 보냄) 바꾼 방장을 뺀 멤버에게 취소 알림을 만든다 (사람이 정함 2026-11-28 '예정된 규칙 변경을 취소만 할 때도 알리자')")
	void cancellingUpcomingChangeNotifiesMembers() {
		assertThat(putRules(rules(Map.of("dailyGoal", 2)))).hasStatus(HttpStatus.OK);
		long cancelledRuleId = latestRuleId();

		assertThat(putRules(rules(Map.of()))).hasStatus(HttpStatus.OK);

		List<NotificationRow> cancels = notifications.ofStudy(studyId, "RULE_CHANGED")
			.stream()
			.filter(row -> row.dedupeKey().endsWith(":cancelled"))
			.toList();
		assertThat(cancels).singleElement().satisfies(row -> {
			assertThat(row.userId()).isEqualTo(member.id());
			assertThat(row.dedupeKey())
				.isEqualTo("RULE:" + studyId + ":" + cancelledRuleId + ":" + member.id() + ":cancelled");
			assertThat(JSON.readTree(row.payload()).path("body").asString()).contains("취소");
			assertThat(JSON.readTree(row.payload()).path("url").asString()).isEqualTo("/studies/" + studyId);
		});
		assertThat(notifications.ofStudy(studyId, "RULE_CHANGED")).hasSize(2);
	}

	@Test
	@DisplayName("바꾸고 취소하기를 두 번 하면 취소 알림도 두 번이다 (취소된 버전 ID가 달라 키가 겹치지 않음)")
	void cancellingTwiceMakesTwoCancelNotifications() {
		putRules(rules(Map.of("dailyGoal", 2)));
		putRules(rules(Map.of()));
		putRules(rules(Map.of("dailyGoal", 3)));
		putRules(rules(Map.of()));

		assertThat(notifications.ofStudy(studyId, "RULE_CHANGED")).filteredOn(row -> row.dedupeKey().endsWith(":cancelled"))
			.extracting(NotificationRow::dedupeKey)
			.doesNotHaveDuplicates()
			.hasSize(2);
	}

	@Test
	@DisplayName("1기 시작 전에 지금 규칙과 같게 보내면(버전은 다시 저장되지만 내용이 같음) 알림이 없다 (S13 만들 것 4, R2 '바뀌면 알림', S13 PR 판단 기록)")
	void sameRulesBeforeFirstSeasonMakeNoNotification() {
		setClock(CREATED_AT);

		assertThat(putRules(rules(Map.of()))).hasStatus(HttpStatus.OK);

		assertThat(notifications.ofStudy(studyId, "RULE_CHANGED")).isEmpty();
	}

	@Test
	@DisplayName("같은 예정 변경을 다시 보내면(예정 버전이 새 ID로 다시 저장되지만 내용이 같음) 알림을 또 만들지 않는다 (S13 만들 것 4, S13 PR 판단 기록)")
	void sameUpcomingChangeTwiceMakesOneNotification() {
		assertThat(putRules(rules(Map.of("dailyGoal", 2)))).hasStatus(HttpStatus.OK);
		assertThat(putRules(rules(Map.of("dailyGoal", 2)))).hasStatus(HttpStatus.OK);

		assertThat(notifications.ofStudy(studyId, "RULE_CHANGED")).hasSize(1);
	}

	@Test
	@DisplayName("알림을 만든 뒤 탈퇴했으면 보내기 직전에 SKIPPED다 (사람이 정함 2026-11-27 '탈퇴한 멤버에게 보내지 않음')")
	void memberWhoLeftAfterChangeIsSkippedWhenSending() {
		notifications.expireLeftovers();
		assertThat(putRules(rules(Map.of("dailyGoal", 2)))).hasStatus(HttpStatus.OK);
		assertThat(delete(member, "/api/studies/" + studyId + "/members/me")).hasStatus(HttpStatus.NO_CONTENT);

		dispatcher.run();

		assertThat(notifications.ofStudy(studyId, "RULE_CHANGED")).singleElement()
			.extracting(NotificationRow::status)
			.isEqualTo("SKIPPED");
	}

	@Test
	@DisplayName("탈퇴한 멤버에게는 보내지 않는다")
	void memberWhoLeftIsNotNotified() {
		assertThat(delete(member, "/api/studies/" + studyId + "/members/me")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(putRules(rules(Map.of("dailyGoal", 2)))).hasStatus(HttpStatus.OK);

		assertThat(notifications.ofStudy(studyId, "RULE_CHANGED")).isEmpty();
	}

	@Test
	@DisplayName("알림 행은 규칙 변경과 같은 트랜잭션에 들어간다: 커밋 전 같은 트랜잭션에서 이미 보이고, 규칙 변경이 롤백되면 알림도 남지 않는다")
	void rolledBackChangeLeavesNoNotification() {
		long before = latestRuleId();
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		transaction.executeWithoutResult(status -> {
			assertThat(putRules(rules(Map.of("dailyGoal", 2)))).hasStatus(HttpStatus.OK);
			// 커밋 뒤 이벤트였다면 아직 없다. 같은 트랜잭션(같은 연결)이라 커밋 전에 보인다.
			assertThat(notifications.ofStudy(studyId, "RULE_CHANGED")).hasSize(1);
			status.setRollbackOnly();
		});

		assertThat(latestRuleId()).isEqualTo(before);
		assertThat(notifications.ofStudy(studyId, "RULE_CHANGED")).isEmpty();
	}

	@Test
	@DisplayName("다시 바꾸면 새 마지막 버전으로 새 알림을 만든다 (예정 버전은 바꿀 때마다 새 ID)")
	void secondChangeMakesAnotherNotification() {
		putRules(rules(Map.of("dailyGoal", 2)));
		putRules(rules(Map.of("dailyGoal", 3)));

		assertThat(notifications.ofStudy(studyId, "RULE_CHANGED")).extracting(NotificationRow::dedupeKey)
			.hasSize(2)
			.last()
			.isEqualTo("RULE:" + studyId + ":" + latestRuleId() + ":" + member.id());
	}

	private MvcTestResult putRules(String body) {
		ObjectNode node = (ObjectNode) JSON.readTree(body);
		node.put("basedOnRuleId", latestRuleId());
		return putJson(owner, "/api/studies/" + studyId + "/rules", JSON.writeValueAsString(node));
	}

	private long latestRuleId() {
		return jdbc.sql("select id from studybot.study_rules where study_id = :studyId order by effective_from desc limit 1")
			.param("studyId", studyId)
			.query(Long.class)
			.single();
	}

	/** R1 기본값으로 채운 규칙 전체에 바꿀 값만 덮어쓴다 (스터디 만들 때의 기본 규칙과 같음). */
	private static String rules(Map<String, Object> overrides) {
		ObjectNode node = JSON.createObjectNode();
		node.put("dailyGoal", 1);
		node.put("dayBoundary", "00:00");
		node.put("graceMinutes", 30);
		node.set("restDays", JSON.createArrayNode());
		node.put("penaltyType", "NONE");
		node.put("fineAmount", 1000);
		node.put("exemptionsPerSeason", 4);
		node.put("reminderOffsetMinutes", 120);
		node.put("selfReportMode", "IMMEDIATE");
		node.put("seasonLengthDays", 28);
		overrides.forEach((key, value) -> node.set(key, JSON.valueToTree(value)));
		return JSON.writeValueAsString(node);
	}

}
