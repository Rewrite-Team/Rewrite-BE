package com.daon.rewrite.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** HTTP 오류 응답의 공통 구조. 필드별 사유가 없는 오류도 details를 빈 배열로 포함한다. */
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
