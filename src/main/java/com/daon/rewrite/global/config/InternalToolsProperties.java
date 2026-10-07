package com.daon.rewrite.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * H2 Console의 Basic Auth 계정에 사용할 내부 도구 설정이다.
 * db-h2의 H2ConsoleSecurityConfig가 바인딩을 활성화하며, 계정과 비밀번호가 비어 있으면 설정 검증에서 거부한다.
 */
@Validated
@ConfigurationProperties("rewrite.internal-tools")
public record InternalToolsProperties(
        @NotBlank String username,
        @NotBlank String password
) {
}
