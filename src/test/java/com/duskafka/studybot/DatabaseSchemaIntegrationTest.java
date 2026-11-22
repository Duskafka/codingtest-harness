package com.duskafka.studybot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

	private List<String> tablesIn(String schema) {
		return jdbc.sql("select table_name from information_schema.tables where table_schema = :schema")
			.param("schema", schema)
			.query(String.class)
			.list();
	}

}
