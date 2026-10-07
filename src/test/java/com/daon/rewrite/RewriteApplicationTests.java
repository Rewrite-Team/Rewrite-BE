package com.daon.rewrite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** test profile의 인메모리 DB·개발용 인증 설정으로 기본 애플리케이션 컨텍스트가 시작되는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
class RewriteApplicationTests {

	@Test
	void contextLoads() {
	}

}
