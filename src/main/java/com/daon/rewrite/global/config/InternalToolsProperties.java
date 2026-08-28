package com.daon.rewrite.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rewrite.internal-tools")
public record InternalToolsProperties(
        @NotBlank String username,
        @NotBlank String password
) {
}
