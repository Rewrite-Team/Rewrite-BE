package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.service.AddInterviewQuestionResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = "jobId")
public record AddInterviewQuestionResponse(
        String jobId
) {

    public static AddInterviewQuestionResponse from(AddInterviewQuestionResult result) {
        return new AddInterviewQuestionResponse(
                result.job().getId()
        );
    }
}
