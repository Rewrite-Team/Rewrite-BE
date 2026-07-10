package com.daon.rewrite.interview.client;

import java.util.List;

public record InterviewQuestionGenerationRequest(
        String companyName,
        String positionTitle,
        String preferences,
        List<InterviewQuestionGenerationAnswer> answers
) {

    public InterviewQuestionGenerationRequest {
        answers = List.copyOf(answers);
    }
}
