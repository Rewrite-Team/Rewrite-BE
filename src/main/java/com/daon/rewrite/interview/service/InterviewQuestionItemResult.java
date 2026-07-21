package com.daon.rewrite.interview.service;

public record InterviewQuestionItemResult(
        String id,
        int order,
        String question,
        String threadId
) {
}
