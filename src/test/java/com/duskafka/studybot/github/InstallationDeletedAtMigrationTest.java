package com.duskafka.studybot.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V6(설치 삭제 시각 {@code deleted_at})는 이미 V5로 운영 중인 DB에도 적용돼야 한다. V5 시절에 DELETED가 된 행은 삭제 시각이 없어서,
 * V6가 {@code updated_at}으로 채운 뒤에 CHECK를 건다 (사람이 정함 2026-11-24 "새 후속 PR로 V6").
 *
 * <p>다른 통합 테스트는 Flyway가 처음부터 끝까지 한 번에 돌린 빈 DB를 쓴다. 이 테스트만 V5까지 올린 DB에 행을 넣고 V6를 돌려야 해서
 * 컨테이너를 따로 띄운다 ({@code SessionRestartTest}와 같은 방식).
 */
class InstallationDeletedAtMigrationTest {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

	@BeforeAll
	static void startDatabase() {
		POSTGRES.start();
	}

	@AfterAll
	static void stopDatabase() {
		POSTGRES.stop();
	}

	@Test
	@DisplayName("S07: V5에서 DELETED가 된 설치는 V6가 updated_at으로 삭제 시각을 채우고, 다른 상태는 비워 둔다")
	void v6BackfillsDeletedAtFromUpdatedAt() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
				POSTGRES.getPassword());
		JdbcClient jdbc = JdbcClient.create(dataSource);
		Instant deletedUpdatedAt = Instant.parse("2026-11-20T09:30:00Z");
		Instant activeUpdatedAt = Instant.parse("2026-11-21T10:00:00Z");

		flyway(dataSource, "5").migrate();
		insertV5Installation(jdbc, 1L, "DELETED", deletedUpdatedAt);
		insertV5Installation(jdbc, 2L, "ACTIVE", activeUpdatedAt);
		insertV5Installation(jdbc, 3L, "SUSPENDED", activeUpdatedAt);

		flyway(dataSource, "6").migrate();

		assertThat(deletedAt(jdbc, 1L)).isEqualTo(deletedUpdatedAt);
		assertThat(deletedAt(jdbc, 2L)).isNull();
		assertThat(deletedAt(jdbc, 3L)).isNull();
	}

	private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
		return Flyway.configure()
			.dataSource(dataSource)
			.schemas("studybot")
			.defaultSchema("studybot")
			.target(target)
			.load();
	}

	/** V5 모양(삭제 시각 컬럼 없음)으로 설치 행을 넣는다. 계정 값은 V5에서도 비워 둘 수 있다. */
	private static void insertV5Installation(JdbcClient jdbc, long installationId, String status, Instant updatedAt) {
		jdbc.sql("""
				insert into studybot.github_installations (installation_id, status, created_at, updated_at)
				values (:id, :status, :createdAt, :updatedAt)
				""")
			.param("id", installationId)
			.param("status", status)
			.param("createdAt", Timestamp.from(updatedAt.minusSeconds(3600)))
			.param("updatedAt", Timestamp.from(updatedAt))
			.update();
	}

	private static @Nullable Instant deletedAt(JdbcClient jdbc, long installationId) {
		return jdbc.sql("select deleted_at from studybot.github_installations where installation_id = :id")
			.param("id", installationId)
			.query((rs, rowNum) -> Optional.ofNullable(rs.getTimestamp("deleted_at")).map(Timestamp::toInstant))
			.single()
			.orElse(null);
	}

}
