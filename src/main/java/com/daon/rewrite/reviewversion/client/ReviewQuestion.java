package com.daon.rewrite.reviewversion.client;

public record ReviewQuestion(
        String questionId,
        int questionOrder,
        String question,
        int maxAnswerLength,
        String originalAnswer
) {
}
