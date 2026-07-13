package com.daon.rewrite.interview.client;

import java.util.List;

public record InterviewMessageFeedbackResult(
        String content,
        String feedbackSummary,
        List<String> feedbackStrengths,
        List<String> feedbackImprovements,
        int score,
        String followUpQuestion
) {

    public InterviewMessageFeedbackResult {
        feedbackStrengths = List.copyOf(feedbackStrengths);
        feedbackImprovements = List.copyOf(feedbackImprovements);
    }
}
