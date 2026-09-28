package com.daon.rewrite.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(requiredProperties = "csrfToken")
public record CsrfTokenResponse(String csrfToken) {
}
