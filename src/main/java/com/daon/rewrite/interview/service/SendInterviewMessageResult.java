package com.daon.rewrite.interview.service;

import com.daon.rewrite.interview.entity.InterviewMessage;
import com.daon.rewrite.llmjob.entity.LlmJob;

public record SendInterviewMessageResult(
        InterviewMessage userMessage,
        LlmJob job
) {
}
