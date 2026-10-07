package com.daon.rewrite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션을 시작하고 Job·자기소개서 상태 SSE의 주기적 DB 조회를 활성화한다.
 * Job의 커밋 후 비동기 실행은 별도의 AsyncConfig가 활성화한다.
 */
@EnableScheduling
@SpringBootApplication
public class RewriteApplication {

	public static void main(String[] args) {
		SpringApplication.run(RewriteApplication.class, args);
	}

}
