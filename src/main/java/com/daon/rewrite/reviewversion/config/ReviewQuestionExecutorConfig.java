package com.daon.rewrite.reviewversion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * ReviewQuestionJobRunner가 문항별 LLM 호출을 병렬 실행할 스레드 풀을 제공한다.
 * 스레드 수와 대기열 용량으로 한 인스턴스에 쌓이는 문항 task의 실행·대기량을 제한한다.
 */
@Configuration
public class ReviewQuestionExecutorConfig {

    @Bean(name = "reviewQuestionExecutor")
    public Executor reviewQuestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("review-question-");
        executor.initialize();
        return executor;
    }
}
