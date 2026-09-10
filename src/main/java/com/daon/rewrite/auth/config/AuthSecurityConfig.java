package com.daon.rewrite.auth.config;

import com.daon.rewrite.auth.service.CsrfTokenService;
import com.daon.rewrite.global.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Base64;
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

    @Bean
    @Order(3)
    SecurityFilterChain authSecurityFilterChain(
            HttpSecurity http,      // Spring Security 설정을 조립하는 빌더
            JwtDecoder jwtDecoder,  // access token JWT 서명/완료/issuer/audience/purpose 를 검증
            BearerTokenResolver bearerTokenResolver,    // 기본 Authorization 헤더 대신 access_token Cookie 에서 JWT 문자열 추출
            AuthenticationEntryPoint authenticationEntryPoint,  // 인증 실패 시 공통 401 UNAUTHORIZED JSON응답을 만드는 처리기
            CsrfTokenService csrfTokenService   // 자체 csrf 토큰 검증. 상태 변경 요청의 X-CSRF-Token 을 발급/검증
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())   // Spring Security 가 기본 제공하는 세션 기반 CSRF 방어 기능을 끔. 대신 CsrfProtectionFilter가 X-CSRF-Token 검증
                .cors(Customizer.withDefaults()) // Spring 컨테이너에서 CorsConfigurationSource Bean을 찾아 CORS 핓터에 연결. 구체적 허용 Origin 과 헤더는 corsConfigurationSource() 가 결정
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 로그인 상태를 HttpSession에 저장하지 않음. 각 요청은 매번 access_token 쿠키의 JWT를 검증하여 독집적으로 인증됨
                .authorizeHttpRequests(authorize -> authorize   // 인가 필터와 URL 별 접근 규칙을 구성
                        .requestMatchers(PUBLIC_AUTH_PATHS.toArray(String[]::new)).permitAll() // 로그인 시작, OAuth callback, CSRF 토큰 발급처럼 인증 없이 호출할 API를 지정
                        .anyRequest().authenticated() // 나머지 API는 유효한 access token이 있어야 한다.
                )

                /*
                 Spring Security 의 Resource Server 기능을 JWT 인증 엔진으로 사용
                 HTTP 요청 -> BearerTokenAuthenticationFilter -> BearerTokenResolver -> AuthenticationManager -> JwtAuthenticationProvider -> JwtDecoder -> JwtAuthenticationToken 생성 -> SecurityContext에 인증 정보 저장
                 일반적인 Resource Server 는 Authorization: Bearer eyJ... 와 같은 헤더에서 토큰을 찾는다.
                 하지만 이 프로젝트에서는 커스텀 BearerTokenResolver를 지정했기 때문에 Cookie: access_token=eyJ... 쿠키에서 찾는다.
                 */
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)       // bearerTokenResolver 는 Cookie에서 access token 문자열을 찾는다. 추출된 JWT는 JWTDecoder로 전달
                        .jwt(jwt -> jwt.decoder(jwtDecoder))    // JwtDecoder로 서명/만료/issuer/audience/purpose를 검증
                        /*
                         검증에 성공하면
                         Spring Security 가 JwtAuthenticationToken을 만들고 SecurityContext에 저장한다.
                         이후 Controller 등에서 인증 사용자를 조회할 수 있다.
                         JWT 가 잘못되었거나 만료된 경우에는
                         Resource Server에 저장한 AuthenticationEntryPoint 가 401 JSON을 작성한다.
                        */
                        .authenticationEntryPoint(authenticationEntryPoint) // 인증이 실패했을 때 HTTP 응답을 만드는 Bean
                        /*
                        다음과 같은 상황에서 호출됨
                        - 보호된 API에 access_token 쿠키가 없음
                        - JWT 형식이 잘못됨
                        - JWT 서명이 잘못됨
                        - JWT가 만료됨
                        - issuer 또는 audience가 다름
                        - purpose가 "access"가 아님
                         */

                        /*
                        Spring Security 필터에서 팔생한 오류는 Controller 까지 도달하지 않기 때문에 SecurityErrorWriter.java 에서 필터 단계에서 직접 JSON 작성
                        응답은 다음과 같은 형태로 통일
                        HTTP/1.1 401 Unauthorized
                        Content-Type: application/json
                        {
                          "error": {
                            "code": "UNAUTHORIZED",
                            "message": "인증이 필요합니다.",
                            "details": []
                          }
                        }
                         */
                ) // Cookie에서 JWT access token을 읽고 서명, 만료, issuer, audience 등을 검증
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))   // 인증 인가 실패 처리 흐름을 구성
                .addFilterAfter(new CsrfProtectionFilter(csrfTokenService), AuthorizationFilter.class) // 인증 인가 이후 자체 CSRF 검증 필터를 실행, CsrfProtectionFilter: 요청에서 토큰을 꺼내 검증, CsrfTokenService: 토큰 발급과 유효성 검증
                .build();   // 설정을 실제 Spring Security 필터 체인으로 완성
    }

    // CorsConfigurationSource 는 컨테이너에 Bean으로 등록되었다가 Spring Security 에 의해 CORS 필터에 연결됨
    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(properties.frontendOrigin()));  // AuthProperties 에 설정된 프론트 주소만 허용
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));   // 허용할 HTTP 메서드 지정
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token"));   // 브라우저가 보낼 수 있는 요청 헤더를 지정
        configuration.setAllowCredentials(true);    // CORS 요청에 인증 정보(credentials)를 포함하도록 허용하는 설정 -> 프론트에서 access_token 쿠키를 포함하여 요청하면(credentials: "include") 서버에서 허용
        configuration.setMaxAge(3600L); // 브라우저가 CORS preflight(OPTIONS) 요청 결과를 3600초(1시간) 동안 캐시

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); // 요청 URL에 따라 어떤 CorsConfiguration을 사용할지 관리하는 객체
        source.registerCorsConfiguration("/**", configuration); // 모든 서버 경로에 CORS 정책을 적용
        return source;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey authSecretKey) {
        return NimbusJwtEncoder.withSecretKey(authSecretKey).build();
    }



    /*
    BearerTokenResolver -> JwtDecoder
    Resource Server 의 JWT 인증 필터가 resolver 결과를 받은 뒤 decoder 로 서명/만료 등을 검증
    */
    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties, SecretKey authSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(authSecretKey)    // authSecretKey: 환경설정에 저장된 Base64 문자열을 HMAC 비밀키 객체로 변환하는 Bean
                .macAlgorithm(MacAlgorithm.HS256)   // 허용 JWS 알고리즘 = HS256 (header 파싱하여 확인한 alg가 HS256이 아니면 signature 검증까지 가지 않음)
                .build();   // JWT 문자열을 파싱하고 서명을 검증하는 Spring Security 구현체

        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuer());  // 토큰의 iss 검증
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<>("aud",     // JwtClaimValidator<T>는 특정 claim(여기서는 "aud")에 대해 검증 조건을 적용
                claim -> claim instanceof java.util.Collection<?> values
                        && values.contains(properties.audience()));
        OAuth2TokenValidator<Jwt> purpose = new JwtClaimValidator<>("purpose", "access"::equals);   // purpose claim이 정확히 "access" 인지 검사
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience, purpose));   // 앞에서 설정한 issuer/ audience/ purpose 3가지 validator를 하나로 결합하여 decoder에 설정
        return decoder; // decoder 에서 하나라도 실패하면 JwtValidationException 계열 인증 실패로 처리
    }


    /*
    HttpServletRequest -> access_token 쿠키 탐색 -> JWT 문자열 또는 null 반환
    Spring Security 가 요청마다 resolver를 호출하여 token 문자열을 꺼냄
    */
    @Bean
    BearerTokenResolver cookieBearerTokenResolver() {
        return request -> {
            /*
            예시 요청 URI : /api/cover-letters
            Context path: /api
            계산 결과: /cover-letters
            공개 API 이면 쿠키 무시
             */
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (PUBLIC_AUTH_PATHS.contains(path)) {
                return null;    // PUBLIC_AUTH_PATHS 경로에서는 resolver 가 access_token Cookie 값을 반환하지 않음
            }
            /*
            HTTP 요청의 Cookie 헤더를 Tomcat 이 Cookie[] 형태로 변환해 제공
            예시 Cookie: theme=dark; access_token=eyJ...; language=ko
            변환하여 반환한 Cookie[]
            [
                new Cookie("theme", "dark"),
                new Cookie("access_token", "eyJ..."),
                new Cookie("language", "ko")
            ]
             */
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }
            return Arrays.stream(cookies)   // 쿠키 배열을 stream으로 변환
                    .filter(cookie -> "access_token".equals(cookie.getName()))  // 이름이 access_token인 쿠키만 남김
                    .map(Cookie::getValue)  // 쿠키 객체에서 값만 꺼냄
                    .filter(value -> !value.isBlank())  // 값이 비어있는 쿠키를 제외
                    .findFirst()    // 첫 유효한 값을 반환
                    .orElse(null);  // 없으면  null 반환
        };
    }

    /*
    인증실패 -> AuthenticationEntryPoint
    JWT가 없거나 잘못되면 이 객체가 401 JSON 작성
    */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) ->
                SecurityErrorWriter.write(response, ErrorCode.UNAUTHORIZED);
    }

    @Bean
    SecretKey authSecretKey(AuthProperties properties) {
        byte[] secret = Base64.getDecoder().decode(properties.jwtSecretBase64());
        if (secret.length < 32) {       // 32 bytes * 8 bits = 256 bits (HmacSHA256용 Java SecretKey 객체 생성을 위해 디코딩 결과가 최소 32bytes 이상이어야 함)
            throw new IllegalStateException("AUTH_JWT_SECRET_BASE64 must decode to at least 32 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }
}
