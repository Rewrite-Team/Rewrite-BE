package com.daon.rewrite.interview.service;

import com.daon.rewrite.interview.entity.InterviewMessageRole;

import java.time.Instant;
import java.util.List;

public record InterviewMessageItemResult(
        String id,
        InterviewMessageRole role,
        String content,
        String feedbackSummary,
        List<String> feedbackStrengths,
        List<String> feedbackImprovements,
        Integer score,
        String followUpQuestion,
        Instant createdAt
) {

    public InterviewMessageItemResult {
        feedbackStrengths = copyOfNullable(feedbackStrengths);
        feedbackImprovements = copyOfNullable(feedbackImprovements);
    }

    private static List<String> copyOfNullable(List<String> values) {
        return values == null ? null : List.copyOf(values);
    }
}
