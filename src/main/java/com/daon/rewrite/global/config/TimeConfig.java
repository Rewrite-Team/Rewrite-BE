package com.daon.rewrite.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 생성·완료 시각과 인증 만료 판단에 사용할 공통 Clock을 제공한다.
 * Clock의 시간대는 서울이며 서비스는 Instant로 시점을 저장하고, 응답 표시 시간 변환은 각 DTO가 담당한다.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
