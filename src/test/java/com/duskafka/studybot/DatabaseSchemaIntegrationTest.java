package com.duskafka.studybot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 앱 테이블은 studybot 스키마에 있고 public에는 없다 (결정 제안서 004 '검증 방법', infra.md 9.4).
 * Flyway 이력 테이블도 같은 스키마에 둔다.
 */
@IntegrationTest
class DatabaseSchemaIntegrationTest {

	@Autowired
	private JdbcClient jdbc;

	@Test
	@DisplayName("S04: users·세션·Flyway 이력 테이블은 studybot 스키마에 있다")
	void appTablesAreInStudybotSchema() {
		assertThat(tablesIn("studybot")).contains("users", "spring_session", "spring_session_attributes",
				"flyway_schema_history");
	}

	@Test
	@DisplayName("S04: public 스키마에는 테이블이 없다 (Supabase Data API에 노출되지 않게)")
	void publicSchemaHasNoTables() {
		assertThat(tablesIn("public")).isEmpty();
	}

	@Test
	@DisplayName("S04: 탈퇴하지 않은 사용자는 github_id가 있어야 한다 (탈퇴하면 5.16대로 지울 수 있다)")
	void activeUserRequiresGithubId() {
		assertThatThrownBy(() -> insertUser(null, null)).isInstanceOf(DataIntegrityViolationException.class);

		insertUser(null, "2026-11-22T00:00:00Z");
		insertUser(9001L, null);
	}

	private void insertUser(Long githubId, String deletedAt) {
		jdbc.sql("insert into studybot.users (github_id, github_login, created_at, deleted_at) "
				+ "values (:githubId, 'schema-test', now(), cast(:deletedAt as timestamptz))")
			.param("githubId", githubId, java.sql.Types.BIGINT)
			.param("deletedAt", deletedAt, java.sql.Types.VARCHAR)
			.update();
	}

	private List<String> tablesIn(String schema) {
		return jdbc.sql("select table_name from information_schema.tables where table_schema = :schema")
			.param("schema", schema)
			.query(String.class)
			.list();
	}

}
