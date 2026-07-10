package com.daon.rewrite.interview.client;

public record InterviewQuestionGenerationAnswer(
        String questionId,
        int questionOrder,
        String question,
        String finalAnswer
) {
}
