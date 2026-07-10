package com.daon.rewrite.interview.client;

import com.daon.rewrite.interview.entity.InterviewQuestionType;

public record InterviewQuestionGenerationResult(
        InterviewQuestionType type,
        String question
) {
}
