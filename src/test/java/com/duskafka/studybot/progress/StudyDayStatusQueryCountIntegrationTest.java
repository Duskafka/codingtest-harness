package com.duskafka.studybot.progress;

import static com.duskafka.studybot.progress.StudyDayRows.NOV_1;
import static com.duskafka.studybot.progress.StudyDayRows.seoul;
import static org.assertj.core.api.Assertions.assertThat;

import com.duskafka.studybot.IntegrationTest;
import com.duskafka.studybot.MutableClock;
import com.duskafka.studybot.TestApi;
import com.duskafka.studybot.progress.StudyDayRows.TestStudy;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 오늘 현황의 쿼리 수가 멤버 수와 상관없이 일정한지 (S10 수용 기준 2, architecture.md 6장 ⑤). Hibernate 통계의 준비한 문장 수
 * ({@link Statistics#getPrepareStatementCount()})로 센다.
 *
 * <p>통계는 테스트 안에서만 켜고 끝나면 끈다. 설정으로 켜면 공유하는 Spring 컨텍스트가 하나 더 생기고 모든 테스트가 통계를 모은다.
 * 테스트가 데이터를 넣을 때 쓰는 {@link JdbcClient}는 Hibernate를 거치지 않아 세지 않는다. 세는 것은 요청 하나가 Hibernate로 낸 문장이다.
 */
@IntegrationTest
class StudyDayStatusQueryCountIntegrationTest {

	private static final LocalDate NOV_20 = LocalDate.of(2026, 11, 20);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private StudyDayAssignment assignment;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private StudyDayRows rows;

	private TestApi api;

	private Statistics statistics;

	@BeforeEach
	void setUp() {
		rows = new StudyDayRows(jdbc);
		api = new TestApi(mvc, jdbc);
		statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	@AfterEach
	void disableStatistics() {
		statistics.setStatisticsEnabled(false);
		statistics.clear();
	}

	/**
	 * 멤버가 {@code memberCount}명인 스터디를 만든다. 첫 멤버 말고는 모두 그날 풀이가 하나씩 있고, 첫 멤버는 면제권을 썼다.
	 * 멤버마다 다른 경로(풀이 있음·면제권·그날 가입)가 모두 나오게 한다.
	 *
	 * @return 첫 멤버의 ID와 스터디
	 */
	private long[] studyWithMembers(int memberCount, String prefix) {
		long first = rows.newUser(prefix + "-0");
		TestStudy study = rows.standardStudy(first);
		rows.putMemberDay(study.studyId(), first, NOV_20, 0, null, seoul(NOV_20, 9, 0));
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		for (int i = 1; i < memberCount; i++) {
			long member = rows.newUser(prefix + "-" + i);
			if (i == memberCount - 1) {
				rows.join(study.studyId(), member, seoul(NOV_20, 8, 0), NOV_20.plusDays(1));
			}
			else {
				rows.join(study.studyId(), member, NOV_1);
			}
			String problemNo = Integer.toString(1000 + i);
			Instant receivedAt = seoul(NOV_20, 10, 0).plusSeconds(i);
			long solveId = (i % 2 == 0) ? rows.githubSolve(member, "BOJ", problemNo, "제목 " + i, receivedAt)
					: rows.solve(member, "BOJ", problemNo, receivedAt);
			transaction.executeWithoutResult(status -> assignment
				.assign(new SolveToAssign(solveId, member, "BOJ", problemNo, receivedAt)));
		}
		return new long[] { first, study.studyId() };
	}

	/** 오늘 현황을 한 번 부르고 Hibernate가 준비한 문장 수를 돌려준다. */
	private long statementsForToday(long userId, long studyId, int expectedMembers) {
		statistics.clear();
		statistics.setStatisticsEnabled(true);
		MvcTestResult result = api.get(userId, "/api/studies/{studyId}/today", studyId);
		statistics.setStatisticsEnabled(false);
		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(TestApi.json(result).get("members").size()).isEqualTo(expectedMembers);
		return statistics.getPrepareStatementCount();
	}

	@Test
	@DisplayName("오늘 현황의 쿼리 수는 멤버 3명과 30명에서 같다")
	void queryCountDoesNotGrowWithMembers() {
		long[] small = studyWithMembers(3, "s10-q3");
		long[] large = studyWithMembers(30, "s10-q30");
		clock.setInstant(seoul(NOV_20, 12, 0));
		statementsForToday(small[0], small[1], 3);

		long forThree = statementsForToday(small[0], small[1], 3);
		long forThirty = statementsForToday(large[0], large[1], 30);

		assertThat(forThree).as("멤버 3명일 때 준비한 문장 수").isPositive();
		assertThat(forThirty).as("멤버 30명일 때 준비한 문장 수 (3명일 때 %d)", forThree).isEqualTo(forThree);
	}

}
