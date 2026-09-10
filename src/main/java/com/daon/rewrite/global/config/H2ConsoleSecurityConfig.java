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

@Configuration
@Profile("db-h2")
@EnableConfigurationProperties(InternalToolsProperties.class)
public class H2ConsoleSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain h2ConsoleSecurityFilterChain(
            HttpSecurity http,
            InternalToolsProperties properties
    ) throws Exception {
        // Spring Security 의 비밀번호 인코더를 생성한다.
        var passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // 환경변수로 내부 도구 계정을 만든다.
        var h2ConsoleUser = User.withUsername(properties.username())
                .password(passwordEncoder.encode(properties.password()))
                .roles("H2_CONSOLE")        // ROLE_H2_CONSOLE 권한 부여
                .build();
        // InMemoryUserDetailsManager : 내부 도구 계정 보관
        var users = new InMemoryUserDetailsManager(h2ConsoleUser);
        // DaoAuthenticationProvider : 입력된 ID와 비밀번호 검증
        var authenticationProvider = new DaoAuthenticationProvider(users);
        // PasswordEncoder : 입력 비밀번호와 인코딩된 비밀번호 비교
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return http
                // H2 Console 하위 경로에만 필터 체인 적용
                .securityMatcher("/h2-console/**")
                .authenticationProvider(authenticationProvider)
                // /h2-console/**의 모든 요청은 ROLE_H2_CONSOLE 권한이 있어야 통과
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasRole("H2_CONSOLE")
                )
                .httpBasic(basic -> basic.realmName("rewrite-internal-tools"))
                // pring Security CSRF를 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                // 기본 Spring Security 설정은 iframe을 차단하므로 H2 화면이 제대로 표시되지 않는다. 따라서 iframe 혀용 설정
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .build();
    }
}
