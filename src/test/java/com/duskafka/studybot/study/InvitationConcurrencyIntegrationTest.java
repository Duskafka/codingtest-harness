package com.duskafka.studybot.study;

import static org.assertj.core.api.Assertions.assertThat;

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
 * 동시 수락 (T20, 5.1 '정원 동시성'). 두 사람이 같은 순간 수락 요청을 보내도 정원(30명)과 사용 횟수를 넘지 않는다.
 * 두 스레드가 준비를 마친 뒤 같은 신호로 요청을 보낸다.
 */
class InvitationConcurrencyIntegrationTest extends StudyTestSupport {

	private static final int CAPACITY = 30;

	@BeforeEach
	void setUp() {
		setClock("2026-11-20T06:00:00Z");
	}

	@Test
	@DisplayName("T20: 마지막 한 자리(29/30)를 두 명이 동시에 수락하면 한 명만 성공하고 정원을 넘지 않는다")
	void lastSeatGoesToOnlyOne() throws Exception {
		TestUser owner = newUser("seat-owner");
		long studyId = createStudy(owner, "GROUP");
		for (int i = 0; i < CAPACITY - 2; i++) {
			insertActiveMember(studyId, newUser("seat-filler"));
		}
		assertThat(activeMemberCount(studyId)).isEqualTo(CAPACITY - 1);
		// 서로 다른 초대 두 개. 초대마다 사용 횟수가 남아 있어도 스터디 정원은 넘지 않아야 한다.
		String firstToken = createInvitation(owner, studyId, null).get("token").asString();
		String secondToken = createInvitation(owner, studyId, null).get("token").asString();

		List<Integer> statuses = acceptConcurrently(new Attempt(newUser("seat-a"), firstToken),
				new Attempt(newUser("seat-b"), secondToken));

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(activeMemberCount(studyId)).isEqualTo(CAPACITY);
	}

	@Test
	@DisplayName("T20: 사용 횟수가 한 번 남은 초대를 두 명이 동시에 수락하면 한 명만 성공한다")
	void lastUseGoesToOnlyOne() throws Exception {
		TestUser owner = newUser("use-owner");
		long studyId = createStudy(owner, "GROUP");
		var invitation = createInvitation(owner, studyId, 1);
		String token = invitation.get("token").asString();

		List<Integer> statuses = acceptConcurrently(new Attempt(newUser("use-a"), token),
				new Attempt(newUser("use-b"), token));

		assertThat(statuses).containsExactlyInAnyOrder(200, 410);
		assertThat(activeMemberCount(studyId)).isEqualTo(2);
		assertThat(jdbc.sql("select used_count from studybot.invitations where id = :id")
			.param("id", invitation.get("id").asLong())
			.query(Integer.class)
			.single()).isEqualTo(1);
	}

	@Test
	@DisplayName("S05: 같은 사람이 동시에 두 번 수락해도 멤버십은 하나이고 사용 횟수는 한 번만 쓴다")
	void sameUserDoubleClickCreatesOneMembership() throws Exception {
		TestUser owner = newUser("double-owner");
		long studyId = createStudy(owner, "GROUP");
		var invitation = createInvitation(owner, studyId, 5);
		String token = invitation.get("token").asString();
		TestUser joiner = newUser("double-joiner");

		List<Integer> statuses = acceptConcurrently(new Attempt(joiner, token), new Attempt(joiner, token));

		assertThat(statuses).containsExactly(200, 200);
		assertThat(membershipsOf(studyId, joiner)).hasSize(1);
		assertThat(jdbc.sql("select used_count from studybot.invitations where id = :id")
			.param("id", invitation.get("id").asLong())
			.query(Integer.class)
			.single()).isEqualTo(1);
	}

	private List<Integer> acceptConcurrently(Attempt... attempts) throws Exception {
		CountDownLatch ready = new CountDownLatch(attempts.length);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(attempts.length);
		try {
			List<Future<MvcTestResult>> futures = new ArrayList<>();
			for (Attempt attempt : attempts) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					go.await();
					return accept(attempt.user(), attempt.token());
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<MvcTestResult> future : futures) {
				statuses.add(future.get(30, TimeUnit.SECONDS).getResponse().getStatus());
			}
			return statuses;
		}
		finally {
			executor.shutdownNow();
		}
	}

	private record Attempt(TestUser user, String token) {
	}

}
