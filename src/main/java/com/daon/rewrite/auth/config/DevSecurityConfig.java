package com.daon.rewrite.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * {@code auth-dev}에서 일반 API의 요청 인증과 CSRF 검증을 생략하는 개발용 보안 설정이다.
 * 현재 사용자 정보는 {@code DevCurrentUserProvider}가 고정값으로 제공한다.
 * 리소스 소유권 검증은 실제 인증과 동일하게 controller/service에서 수행한다.
 */
@Configuration
@Profile("auth-dev")
public class DevSecurityConfig {

    @Bean
    SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
