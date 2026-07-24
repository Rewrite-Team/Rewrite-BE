package com.daon.rewrite.global.response;

public record SuccessResponse(
        boolean success
) {
    public static SuccessResponse completed() {
        return new SuccessResponse(true);
    }
}
