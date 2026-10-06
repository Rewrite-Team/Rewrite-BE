package com.daon.rewrite.interview.client.question;

public record InterviewQuestionGenerationAnswer(
        String questionId,
        int questionOrder,
        String question,
        String finalAnswer
) {
}
