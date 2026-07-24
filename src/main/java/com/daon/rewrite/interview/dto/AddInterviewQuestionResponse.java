package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.service.AddInterviewQuestionResult;

public record AddInterviewQuestionResponse(
        String jobId
) {

    public static AddInterviewQuestionResponse from(AddInterviewQuestionResult result) {
        return new AddInterviewQuestionResponse(
                result.job().getId()
        );
    }
}
