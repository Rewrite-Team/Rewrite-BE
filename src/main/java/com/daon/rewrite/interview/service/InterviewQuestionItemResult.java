package com.daon.rewrite.interview.service;

public record InterviewQuestionItemResult(
        String id,
        String sourceReviewVersionId,
        int order,
        String question,
        String threadId
) {
}
