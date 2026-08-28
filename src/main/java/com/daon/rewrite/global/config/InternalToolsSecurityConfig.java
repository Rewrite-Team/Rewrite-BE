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
@Profile("!local & !test")
@EnableConfigurationProperties(InternalToolsProperties.class)
public class InternalToolsSecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain internalToolsSecurityFilterChain(
            HttpSecurity http,
            InternalToolsProperties properties
    ) throws Exception {
        var passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        var internalToolsUser = User.withUsername(properties.username())
                .password(passwordEncoder.encode(properties.password()))
                .roles("INTERNAL_TOOLS")    // ROLE_INTERNAL_TOOLS 권한 부여
                .build();
        var users = new InMemoryUserDetailsManager(internalToolsUser);
        var authenticationProvider = new DaoAuthenticationProvider(users);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return http
                .securityMatcher(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**"
                )
                .authenticationProvider(authenticationProvider)
                // Swagger 관련 경로의 모든 요청은 ROLE_INTERNAL_TOOLS 권한 필요
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasRole("INTERNAL_TOOLS")
                )
                .httpBasic(basic -> basic.realmName("rewrite-internal-tools"))
                /*
                Swagger/OpenAPI 경로에서 Spring Security 기본 CSRF 검사를 끔
                Swagger에서 실제 API를 실행할 때는 요청 경로가 다르다.
                GET /swagger-ui/index.html -> InternalToolsSecurityConfig
                POST /cover-letters        -> AuthSecurityConfig
                따라서 Swagger 화면의 CSRF를 껐다고 일반 API의 CSRF까지 꺼지는 것은 아님
                 */
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .build();
    }
}
