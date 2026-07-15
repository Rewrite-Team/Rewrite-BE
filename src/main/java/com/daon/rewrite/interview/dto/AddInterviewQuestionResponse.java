package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.service.AddInterviewQuestionResult;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;

public record AddInterviewQuestionResponse(
        String interviewSessionId,
        String jobId,
        InterviewSessionStatus status,
        LlmJobStatus jobStatus
) {

    public static AddInterviewQuestionResponse from(AddInterviewQuestionResult result) {
        return new AddInterviewQuestionResponse(
                result.interviewSession().getId(),
                result.job().getId(),
                result.interviewSession().getStatus(),
                result.job().getStatus()
        );
    }
}
