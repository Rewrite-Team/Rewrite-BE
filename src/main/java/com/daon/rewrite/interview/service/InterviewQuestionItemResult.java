package com.daon.rewrite.interview.service;

import com.daon.rewrite.interview.entity.InterviewQuestionType;

public record InterviewQuestionItemResult(
        String id,
        String sourceReviewVersionId,
        int order,
        InterviewQuestionType type,
        String question,
        String threadId
) {
}
