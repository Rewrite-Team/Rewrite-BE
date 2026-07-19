package com.daon.rewrite.reviewversion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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
