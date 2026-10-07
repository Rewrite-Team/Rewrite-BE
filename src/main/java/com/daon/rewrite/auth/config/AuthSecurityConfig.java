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

/**
 * {@code auth-real}에서 Cookie 기반 JWT 인증과 credential CORS를 구성한다.
 * 요청은 access token 인증, 경로별 접근 허용 판단, 상태 변경 요청의 CSRF 검증 순으로 통과한다.
 * 개발용 {@code auth-dev}의 인증 생략 설정은 {@link DevSecurityConfig}가 담당한다.
 */
@Configuration
@Profile("auth-real")
@EnableConfigurationProperties(AuthProperties.class)
public class AuthSecurityConfig {

    // access token 없이 시작하거나 인증을 복구하는 경로다. refresh·logout의 CSRF 검증은 유지한다.
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

    /** H2 Console 전용 체인 등 우선순위가 높은 체인에서 선택하지 않은 요청에 적용한다. */
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
                // 보호 경로는 인증 실패를 먼저 반환한다. 공개 경로도 상태 변경 메서드이면 CSRF 검증을 거친다.
                .addFilterAfter(new CsrfProtectionFilter(csrfTokenService), AuthorizationFilter.class)
                .build();
    }

    /** 브라우저가 두 프론트엔드 Origin에서 Cookie와 CSRF 헤더를 포함해 API를 호출하도록 허용한다. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
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

    /** {@code AuthTokenService}가 Rewrite access JWT를 발급할 때 사용하는 서명기다. */
    @Bean
    JwtEncoder jwtEncoder(SecretKey authSecretKey) {
        return NimbusJwtEncoder.withSecretKey(authSecretKey).build();
    }

    /** 서명·유효 시각·발급자·대상·용도를 검증해 Rewrite access JWT만 인증에 사용한다. */
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

    /**
     * 보호 경로의 {@code access_token} Cookie를 Resource Server에 전달한다.
     * 공개 경로에서는 Cookie를 읽지 않아 만료되거나 잘못된 access token이 로그인·인증 복구를 막지 않는다.
     * {@code permitAll}만으로는 앞선 JWT 인증 필터의 실패를 피할 수 없으므로 여기서 제외한다.
     */
    @Bean
    BearerTokenResolver cookieBearerTokenResolver() {
        return request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
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

    // 접근 허용 경로와 토큰 추출 제외 경로가 일치해야 공개 API와 문서에 오래된 Cookie로 접근할 수 있다.
    private static boolean isPublicPath(String path) {
        return PUBLIC_AUTH_PATHS.contains(path)
                || path.equals("/swagger-ui.html")
                || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs")
                || path.startsWith("/v3/api-docs/");
    }

    /** 인증 필터에서 거부된 요청에 공통 UNAUTHORIZED 응답을 작성한다. */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) ->
                SecurityErrorWriter.write(response, ErrorCode.UNAUTHORIZED);
    }

    /** JWT와 CSRF 서명에 함께 주입할 키를 생성하며, 시작 시 Base64 형식과 최소 키 길이를 확인한다. */
    @Bean
    SecretKey authSecretKey(AuthProperties properties) {
        byte[] secret = Base64.getDecoder().decode(properties.jwtSecretBase64());
        if (secret.length < 32) { // HS256 서명 키는 최소 256 bit(32 byte)를 요구한다.
            throw new IllegalStateException("AUTH_JWT_SECRET_BASE64 must decode to at least 32 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }
}
