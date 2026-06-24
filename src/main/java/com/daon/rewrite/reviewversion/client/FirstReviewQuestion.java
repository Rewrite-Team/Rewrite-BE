package com.daon.rewrite.reviewversion.client;

public record FirstReviewQuestion(
        String questionId,
        int questionOrder,
        String question,
        int maxAnswerLength,
        String originalAnswer
) {
}
