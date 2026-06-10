package com.daon.rewrite.global.response;

import java.util.List;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            List<ErrorDetail> details
    ) {
    }

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
