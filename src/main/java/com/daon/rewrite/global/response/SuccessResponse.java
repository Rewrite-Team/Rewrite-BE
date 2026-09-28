package com.daon.rewrite.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = "success")
public record SuccessResponse(
        boolean success
) {
    public static SuccessResponse completed() {
        return new SuccessResponse(true);
    }
}
