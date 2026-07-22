package com.daon.rewrite.interview.service;

import java.util.List;

public record InterviewMessageListResult(
        String jobId,
        List<InterviewMessageItemResult> items
) {

    public InterviewMessageListResult {
        items = List.copyOf(items);
    }
}
