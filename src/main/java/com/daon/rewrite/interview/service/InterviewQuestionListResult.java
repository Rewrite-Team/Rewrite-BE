package com.daon.rewrite.interview.service;

import java.util.List;

public record InterviewQuestionListResult(
        List<InterviewQuestionItemResult> items,
        String nextCursor
) {

    public InterviewQuestionListResult {
        items = List.copyOf(items);
    }
}
