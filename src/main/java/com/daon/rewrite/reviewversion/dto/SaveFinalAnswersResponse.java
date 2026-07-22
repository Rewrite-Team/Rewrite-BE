package com.daon.rewrite.reviewversion.dto;

public record SaveFinalAnswersResponse(
        boolean success
) {
    public static SaveFinalAnswersResponse completed() {
        return new SaveFinalAnswersResponse(true);
    }
}
