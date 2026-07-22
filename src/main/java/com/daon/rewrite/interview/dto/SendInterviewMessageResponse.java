package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.service.SendInterviewMessageResult;
public record SendInterviewMessageResponse(
        String userMessageId,
        String jobId
) {

    public static SendInterviewMessageResponse from(SendInterviewMessageResult result) {
        return new SendInterviewMessageResponse(
                result.userMessage().getId(),
                result.job().getId()
        );
    }
}
