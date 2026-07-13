package com.daon.rewrite.interview.client;

import java.util.List;

public record InterviewMessageFeedbackRequest(
        String originalQuestion,
        List<InterviewMessageFeedbackMessage> messages
) {

    public InterviewMessageFeedbackRequest {
        messages = List.copyOf(messages);
    }
}
