package com.daon.rewrite.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = "error")
public record ErrorResponse(ErrorBody error) {

    @Schema(requiredProperties = {"code", "message", "details"})
    public record ErrorBody(
            String code,
            String message,
            List<ErrorDetail> details
    ) {
    }

    @Schema(requiredProperties = {"field", "reason"})
    public record ErrorDetail(
            String field,
            String reason
    ) {
    }

    public static ErrorResponse of(String code, String message) {
        return of(code, message, List.of());
    }

    public static ErrorResponse of(String code, String message, List<ErrorDetail> details) {
        return new ErrorResponse(new ErrorBody(code, message, List.copyOf(details)));
    }
}
