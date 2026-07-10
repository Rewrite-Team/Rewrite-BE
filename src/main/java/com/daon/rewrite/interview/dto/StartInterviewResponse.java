package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.service.StartInterviewResult;

public record StartInterviewResponse(
        String interviewSessionId,
        String jobId,
        InterviewSessionStatus status
) {

    public static StartInterviewResponse from(StartInterviewResult result) {
        return new StartInterviewResponse(
                result.interviewSession().getId(),
                result.job() == null ? null : result.job().getId(),
                result.interviewSession().getStatus()
        );
    }
}
