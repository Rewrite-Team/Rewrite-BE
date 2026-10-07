package com.daon.rewrite.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code auth-real}에서 바인딩하고 필수값을 검증하는 인증 설정이다.
 * {@code AuthSecurityConfig}는 CORS·JWT 검증·서명 키 생성에, {@code AuthTokenService}는 JWT 발급에 사용한다.
 * {@code KakaoAuthController}는 검증된 target에 해당하는 성공·로그인 URL로 OAuth 결과를 돌려보낸다.
 *
 * @param issuer access JWT 발급 및 검증에 사용할 발급자
 * @param audience access JWT 발급 및 검증에 사용할 대상
 * @param jwtSecretBase64 JWT·CSRF 서명 키를 만들 Base64 값
 * @param frontendOrigin 운영 프론트엔드의 CORS 허용 Origin
 * @param frontendSuccessUrl production target의 로그인 성공 목적지
 * @param frontendLoginUrl production target의 로그인 취소·실패 목적지
 * @param localFrontendOrigin 로컬 프론트엔드의 CORS 허용 Origin
 * @param localFrontendSuccessUrl local target의 로그인 성공 목적지
 * @param localFrontendLoginUrl local target의 로그인 취소·실패 목적지
 * @param kakao 카카오 인가 URL 생성과 토큰 교환·사용자 조회에 사용할 설정
 */
@Validated
@ConfigurationProperties("rewrite.auth")
public record AuthProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String jwtSecretBase64,
        @NotBlank String frontendOrigin,
        @NotBlank String frontendSuccessUrl,
        @NotBlank String frontendLoginUrl,
        @NotBlank String localFrontendOrigin,
        @NotBlank String localFrontendSuccessUrl,
        @NotBlank String localFrontendLoginUrl,
        @NotNull @Valid Kakao kakao
) {

    public String frontendSuccessUrl(FrontendTarget target) {
        return target == FrontendTarget.LOCAL ? localFrontendSuccessUrl : frontendSuccessUrl;
    }

    public String frontendLoginUrl(FrontendTarget target) {
        return target == FrontendTarget.LOCAL ? localFrontendLoginUrl : frontendLoginUrl;
    }

    /**
     * 카카오 로그인 시작에서는 clientId·redirectUri·authorizeUri로 인가 URL을 만든다.
     * {@code RestKakaoClient}는 clientSecret을 포함해 tokenUri로 토큰을 교환하고 userInfoUri에서 사용자를 조회한다.
     * redirectUri는 프론트엔드 목적지와 별개인 백엔드 OAuth callback 주소다.
     */
    public record Kakao(
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            @NotBlank String redirectUri,
            @NotBlank String authorizeUri,
            @NotBlank String tokenUri,
            @NotBlank String userInfoUri
    ) {
    }
}
