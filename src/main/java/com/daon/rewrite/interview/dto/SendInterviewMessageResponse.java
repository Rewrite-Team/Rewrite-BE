package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.service.SendInterviewMessageResult;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(requiredProperties = {"userMessageId", "jobId"})
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
