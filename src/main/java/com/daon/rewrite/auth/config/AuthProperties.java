package com.daon.rewrite.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rewrite.auth")
public record AuthProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String jwtSecretBase64,
        @NotBlank String frontendOrigin,
        @NotBlank String frontendSuccessUrl,
        @NotBlank String frontendLoginUrl,
        @NotNull @Valid Kakao kakao
) {

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
