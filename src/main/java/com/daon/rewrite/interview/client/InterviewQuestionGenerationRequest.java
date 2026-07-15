package com.daon.rewrite.interview.client;

import java.util.List;

public record InterviewQuestionGenerationRequest(
        String companyName,
        String positionTitle,
        String preferences,
        int questionCount,
        List<String> existingQuestions,
        List<InterviewQuestionGenerationAnswer> answers
) {

    public InterviewQuestionGenerationRequest {
        existingQuestions = List.copyOf(existingQuestions);
        answers = List.copyOf(answers);
    }
}
