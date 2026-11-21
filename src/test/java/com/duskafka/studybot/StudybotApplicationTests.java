package com.duskafka.studybot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StudybotApplicationTests {

	@Test
	void contextLoads() {
	}

}
