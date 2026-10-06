package com.daon.rewrite.auth.config;

import com.daon.rewrite.auth.service.CsrfTokenService;
import com.daon.rewrite.global.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.config.Customizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Profile("auth-real")
@EnableConfigurationProperties(AuthProperties.class)
public class AuthSecurityConfig {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/auth/kakao/authorize",
            "/auth/kakao/callback",
            "/auth/csrf-token",
            "/auth/refresh",
            "/auth/logout"
    );
    private static final String[] PUBLIC_DOCUMENTATION_PATHS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    @Bean
    @Order(3)
    SecurityFilterChain authSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            BearerTokenResolver bearerTokenResolver,
            AuthenticationEntryPoint authenticationEntryPoint,
            CsrfTokenService csrfTokenService
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // 쿠키 인증의 CSRF는 CsrfProtectionFilter에서 검증한다.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_AUTH_PATHS.toArray(String[]::new)).permitAll()
                        .requestMatchers(PUBLIC_DOCUMENTATION_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                // HttpOnly Cookie로 전달한 access token을 Resource Server의 JWT 검증에 연결한다.
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                // 인증·인가 오류가 CSRF 오류보다 먼저 반환되도록 뒤에 배치한다.
                .addFilterAfter(new CsrfProtectionFilter(csrfTokenService), AuthorizationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        // 로컬·운영 프론트엔드의 cross-origin Cookie 요청을 허용한다.
        configuration.setAllowedOrigins(List.copyOf(new LinkedHashSet<>(List.of(
                properties.frontendOrigin(),
                properties.localFrontendOrigin()
        ))));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey authSecretKey) {
        return NimbusJwtEncoder.withSecretKey(authSecretKey).build();
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties, SecretKey authSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(authSecretKey)
                .macAlgorithm(MacAlgorithm.HS256) // 토큰 헤더가 지정한 다른 서명 알고리즘은 허용하지 않는다.
                .build();

        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<>("aud",
                claim -> claim instanceof java.util.Collection<?> values
                        && values.contains(properties.audience()));
        OAuth2TokenValidator<Jwt> purpose = new JwtClaimValidator<>("purpose", "access"::equals); // 인증에는 access 용도의 JWT만 허용한다.
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience, purpose));
        return decoder;
    }

    @Bean
    BearerTokenResolver cookieBearerTokenResolver() {
        return request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            // 오래된 access Cookie가 로그인·갱신·로그아웃과 공개 문서 접근을 막지 않도록 무시한다.
            if (isPublicPath(path)) {
                return null;
            }
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }
            return Arrays.stream(cookies)
                    .filter(cookie -> "access_token".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse(null);
        };
    }

    private static boolean isPublicPath(String path) {
        return PUBLIC_AUTH_PATHS.contains(path)
                || path.equals("/swagger-ui.html")
                || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs")
                || path.startsWith("/v3/api-docs/");
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint() {
        // 필터 단계의 인증 오류는 Controller에 도달하지 않으므로 여기서 공통 응답을 작성한다.
        return (request, response, exception) ->
                SecurityErrorWriter.write(response, ErrorCode.UNAUTHORIZED);
    }

    @Bean
    SecretKey authSecretKey(AuthProperties properties) {
        byte[] secret = Base64.getDecoder().decode(properties.jwtSecretBase64());
        if (secret.length < 32) { // HS256 서명 키는 최소 256 bit(32 byte)를 요구한다.
            throw new IllegalStateException("AUTH_JWT_SECRET_BASE64 must decode to at least 32 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }
}
