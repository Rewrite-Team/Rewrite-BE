package com.daon.rewrite.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Job 생성 트랜잭션의 커밋 후 이벤트 리스너가 별도 스레드에서 실행되도록 {@code @Async} 처리를 활성화한다.
 * 작업의 DB 변경 트랜잭션은 비동기 스레드에서 호출하는 각 서비스가 담당한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
