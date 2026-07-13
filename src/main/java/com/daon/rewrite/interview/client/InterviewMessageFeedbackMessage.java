package com.daon.rewrite.interview.client;

import com.daon.rewrite.interview.entity.InterviewMessageRole;

public record InterviewMessageFeedbackMessage(
        InterviewMessageRole role,
        String content
) {
}
