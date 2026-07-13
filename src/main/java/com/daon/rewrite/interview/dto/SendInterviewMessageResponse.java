package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.service.SendInterviewMessageResult;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;

public record SendInterviewMessageResponse(
        String userMessageId,
        String jobId,
        LlmJobStatus status
) {

    public static SendInterviewMessageResponse from(SendInterviewMessageResult result) {
        return new SendInterviewMessageResponse(
                result.userMessage().getId(),
                result.job().getId(),
                result.job().getStatus()
        );
    }
}
