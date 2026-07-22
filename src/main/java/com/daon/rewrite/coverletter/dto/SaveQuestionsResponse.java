package com.daon.rewrite.coverletter.dto;

public record SaveQuestionsResponse(
        boolean success
) {
    public static SaveQuestionsResponse completed() {
        return new SaveQuestionsResponse(true);
    }
}
