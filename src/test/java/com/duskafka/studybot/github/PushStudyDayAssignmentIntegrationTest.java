package com.duskafka.studybot.github;

import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.duskafka.studybot.github.GithubConnectionRows.TestUser;
import com.duskafka.studybot.github.PushPayloads.Commit;
import com.duskafka.studybot.progress.StudyDayRows;
import com.duskafka.studybot.progress.StudyDayRows.StudyDaySolveRow;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * push 웹훅부터 스터디 일 배정까지 (S09 수용 기준 T4·T5·T7, 5.3 'push 처리 순서' 5). 실제 push 처리기(S08)와 배정(S09)을 부른다
 * ({@link WebhookTestConfiguration.RecordingPushHandler#passThrough()}). 인정 시각은 수신함의 수신 시각이라 테스트 시계로 정한다.
 *
 * <p>스터디는 {@link StudyDayRows#standardStudy}: 서울, 기준 시각 00:00, 유예 30분, 목표 1문제, 1기 11-01~11-28.
 */
class PushStudyDayAssignmentIntegrationTest extends WebhookTestSupport {

	private static final String BOJ_MESSAGE = "[Silver I] Title: 미로 탐색, Time: 76 ms, Memory: 14532 KB -BaekjoonHub";

	private static final String BOJ_FILE = "백준/Silver/2178. 미로 탐색/미로 탐색.java";

	private static final String PROGRAMMERS_MESSAGE = "[level 1] Title: 문자열 내 p와 y의 개수, Time: 0.03 ms, Memory: 77.8 MB -BaekjoonHub";

	private static final String PROGRAMMERS_FILE = "프로그래머스/1/12916. 문자열 내 p와 y의 개수/문자열 내 p와 y의 개수.java";

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	private static final LocalDate NOV_21 = LocalDate.of(2026, 11, 21);

	private GithubConnectionRows connections;

	private StudyDayRows studyDays;

	@BeforeEach
	void setUp() {
		pushHandler.passThrough();
		connections = new GithubConnectionRows(jdbc);
		studyDays = new StudyDayRows(jdbc);
	}

	/** 레포를 연결한 사용자와 그 사용자가 멤버인 기본 스터디. */
	private Pusher newPusher(String login) {
		TestUser user = connections.newUser(login, clock.instant());
		long installationId = connections.insertLinkedInstallation(user, clock.instant());
		long repoId = GithubConnectionRows.newRepoId();
		connections.insertLink(user, installationId, repoId, user.login() + "/algorithm", "ACTIVE", clock.instant());
		return new Pusher(user.id(), repoId, studyDays.standardStudy(user.id()));
	}

	private MvcTestResult push(Pusher pusher, List<Commit> commits) {
		return sendSigned("push", newDeliveryId(), PushPayloads.push(pusher.repoId(), PushPayloads.MAIN, commits));
	}

	private int solveRecordCount(Pusher pusher) {
		return jdbc.sql("select count(*) from studybot.solve_records where user_id = :userId")
			.param("userId", pusher.userId())
			.query(Integer.class)
			.single();
	}

	@Test
	@DisplayName("T4: 같은 날 같은 문제를 두 번 커밋한 push → 풀이 기록은 2개, 스터디 일에는 1문제로 센다")
	void t4SameProblemCommittedTwiceCountsOnce() {
		clock.setInstant(seoul(NOV_21, 9, 0));
		Pusher pusher = newPusher("t4");

		assertThat(push(pusher, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE), Commit.modifying(BOJ_MESSAGE, BOJ_FILE))))
			.hasStatus(202);

		assertThat(solveRecordCount(pusher)).isEqualTo(2);
		assertThat(studyDays.studyDaySolves(pusher.study().studyId(), pusher.userId()))
			.extracting(StudyDaySolveRow::studyDay, StudyDaySolveRow::problemNo)
			.containsExactly(tuple(NOV_21, "2178"));
		assertThat(studyDays.solvedCount(pusher.study().studyId(), pusher.userId(), NOV_21)).isEqualTo(1);
	}

	@Test
	@DisplayName("T5: force push로 SHA가 바뀐 같은 문제가 다시 와도 같은 날 1문제")
	void t5ForcePushWithNewShaCountsOnce() {
		clock.setInstant(seoul(NOV_21, 9, 0));
		Pusher pusher = newPusher("t5");
		assertThat(push(pusher, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE)))).hasStatus(202);

		clock.advance(Duration.ofHours(1));
		assertThat(push(pusher, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE)))).hasStatus(202);

		assertThat(solveRecordCount(pusher)).isEqualTo(2);
		assertThat(studyDays.studyDaySolveCount(pusher.study().studyId(), pusher.userId(), NOV_21)).isEqualTo(1);
		assertThat(studyDays.solvedCount(pusher.study().studyId(), pusher.userId(), NOV_21)).isEqualTo(1);
	}

	@Test
	@DisplayName("T7: 00:05에 push 2개(서로 다른 문제)가 동시에 도착 → 전날 1, 오늘 1. 5번 반복해도 같고 두 수신함 모두 PROCESSED")
	void t7ConcurrentPushesInGraceSplitBetweenDays() throws Exception {
		Instant nov21At0005 = seoul(NOV_21, 0, 5);
		for (int round = 0; round < 5; round++) {
			clock.setInstant(nov21At0005);
			Pusher pusher = newPusher("t7-" + round);
			List<String> deliveries = List.of(newDeliveryId(), newDeliveryId());
			List<byte[]> bodies = List.of(
					PushPayloads.push(pusher.repoId(), PushPayloads.MAIN, List.of(Commit.adding(BOJ_MESSAGE, BOJ_FILE))),
					PushPayloads.push(pusher.repoId(), PushPayloads.MAIN,
							List.of(Commit.adding(PROGRAMMERS_MESSAGE, PROGRAMMERS_FILE))));

			sendConcurrently(deliveries, bodies);

			assertThat(deliveries).allSatisfy(delivery -> assertThat(onlyInboxRow(delivery).status()).isEqualTo("PROCESSED"));
			long studyId = pusher.study().studyId();
			assertThat(studyDays.solvedCount(studyId, pusher.userId(), NOV_20)).as("round %d 전날", round).isEqualTo(1);
			assertThat(studyDays.solvedCount(studyId, pusher.userId(), NOV_21)).as("round %d 오늘", round).isEqualTo(1);
		}
	}

	private void sendConcurrently(List<String> deliveries, List<byte[]> bodies) throws Exception {
		CountDownLatch ready = new CountDownLatch(deliveries.size());
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(deliveries.size());
		try {
			List<Future<MvcTestResult>> results = new ArrayList<>();
			for (int i = 0; i < deliveries.size(); i++) {
				String delivery = deliveries.get(i);
				byte[] body = bodies.get(i);
				results.add(executor.submit(() -> {
					ready.countDown();
					go.await();
					return sendSigned("push", delivery, body);
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			for (Future<MvcTestResult> result : results) {
				assertThat(result.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(202);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	private record Pusher(long userId, long repoId, TestStudy study) {
	}

}
