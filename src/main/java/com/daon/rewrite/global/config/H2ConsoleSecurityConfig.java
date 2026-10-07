package com.daon.rewrite.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * db-h2에서 H2 Console만 내부 도구 계정의 Basic Auth로 보호한다.
 * 일반 API보다 먼저 선택되는 전용 체인이므로 개발용 인증 생략이나 사용자 JWT 정책과 분리된다.
 */
@Configuration
@Profile("db-h2")
@EnableConfigurationProperties(InternalToolsProperties.class)
public class H2ConsoleSecurityConfig {

    /**
     * 설정에서 받은 계정을 메모리에 등록하고 H2_CONSOLE 역할이 있는 요청만 허용한다.
     * Console의 form 요청을 위해 이 경로에서만 CSRF를 생략하고, 화면 구성에 필요한 같은 Origin의 iframe을 허용한다.
     * 인증은 HTTP 세션에 저장하지 않고 Basic Auth로 처리한다.
     */
    @Bean
    @Order(1)
    SecurityFilterChain h2ConsoleSecurityFilterChain(
            HttpSecurity http,
            InternalToolsProperties properties
    ) throws Exception {
        var passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        var h2ConsoleUser = User.withUsername(properties.username())
                .password(passwordEncoder.encode(properties.password()))
                .roles("H2_CONSOLE")
                .build();
        var users = new InMemoryUserDetailsManager(h2ConsoleUser);
        var authenticationProvider = new DaoAuthenticationProvider(users);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return http
                // CSRF·iframe 예외가 일반 API에 적용되지 않도록 Console 경로로 한정한다.
                .securityMatcher("/h2-console/**")
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasRole("H2_CONSOLE")
                )
                .httpBasic(basic -> basic.realmName("rewrite-internal-tools"))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .build();
    }
}
