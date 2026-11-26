package com.duskafka.studybot.github;

import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import com.duskafka.studybot.github.PushPayloads.Commit;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.StudyDaySolveRow;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * push 웹훅으로 생긴 확인 대기에 멤버가 문제를 지정한다 (S10 수용 기준 T26, 5.4 '확인 대기', R6). 실제 push 처리기(S08)가 만든 확인 대기
 * 기록에 {@code PATCH /api/me/solves/{id}}로 문제를 지정하고, 원래 수신 시각(수신함의 수신 시각)의 스터디 일에 반영되는지 본다.
 *
 * <p>스터디는 {@link StudyDayRows#standardStudy}: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 1기 11-01~11-28.
 */
class PendingSolveIdentificationIntegrationTest extends WebhookTestSupport {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_22 = LocalDate.of(2026, 11, 22);

	private GithubConnectionRows connections;

	private StudyDayRows studyDays;

	private TestApi api;

	@BeforeEach
	void setUp() {
		pushHandler.passThrough();
		connections = new GithubConnectionRows(jdbc);
		studyDays = new StudyDayRows(jdbc);
		api = new TestApi(mvc, jdbc);
	}

	@Test
	@DisplayName("T26: push로 생긴 확인 대기에 이틀 뒤 문제를 지정하면 수신한 날(11/20)의 풀이로 센다")
	void t26PendingFromPushCountsOnTheReceivedDay() {
		clock.setInstant(seoul(NOV_20, 23, 50));
		TestUser user = connections.newUser("s10-t26-push", clock.instant());
		long installationId = connections.insertLinkedInstallation(user, clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		connections.insertLink(user, installationId, repoId, user.login() + "/algorithm", "ACTIVE", clock.instant());
		TestStudy study = studyDays.standardStudy(user.id());
		Commit commit = Commit.adding("별 찍기 풀이", "codetree/별 찍기/Main.java");
		assertThat(sendSigned("push", newDeliveryId(), PushPayloads.push(repoId, PushPayloads.MAIN, List.of(commit))))
			.hasStatus(202);
		long pendingId = jdbc
			.sql("select id from studybot.solve_records where user_id = :userId and status = 'PENDING_IDENTIFICATION'")
			.param("userId", user.id())
			.query(Long.class)
			.single();
		assertThat(studyDays.studyDaySolves(study.studyId(), user.id())).isEmpty();

		clock.setInstant(seoul(NOV_22, 10, 0));
		MvcTestResult result = api.patchJson(user.id(), "/api/me/solves/{id}",
				"{\"platform\": \"BOJ\", \"problemNo\": \"2438\"}", pendingId);

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(TestApi.json(result).get("rawPath").asString()).isEqualTo("codetree/별 찍기/Main.java");
		assertThat(studyDays.studyDaySolves(study.studyId(), user.id()))
			.extracting(StudyDaySolveRow::studyDay, StudyDaySolveRow::solveRecordId, StudyDaySolveRow::problemNo)
			.containsExactly(tuple(NOV_20, pendingId, "2438"));
		assertThat(studyDays.solvedCount(study.studyId(), user.id(), NOV_20)).isEqualTo(1);
	}

}
