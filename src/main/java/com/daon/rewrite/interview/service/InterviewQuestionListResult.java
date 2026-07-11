package com.daon.rewrite.interview.service;

import java.util.List;

public record InterviewQuestionListResult(
        String interviewSessionId,
        List<InterviewQuestionItemResult> items
) {

    public InterviewQuestionListResult {
        items = List.copyOf(items);
    }
}
