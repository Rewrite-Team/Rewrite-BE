package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.service.StartInterviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"interviewSessionId", "jobId", "status"})
public record StartInterviewResponse(
        String interviewSessionId,
        @Schema(nullable = true)
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
