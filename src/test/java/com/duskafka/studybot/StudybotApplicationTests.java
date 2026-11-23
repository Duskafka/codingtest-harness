package com.duskafka.studybot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// S07부터 GitHub App private key가 있어야 앱이 뜬다. 테스트 키와 WireMock 주소를 주는 설정을 함께 가져온다.
@Import({ TestcontainersConfiguration.class, GithubAppTestConfiguration.class })
@SpringBootTest
class StudybotApplicationTests {

	@Test
	void contextLoads() {
	}

}
