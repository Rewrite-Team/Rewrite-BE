package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.service.StartInterviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

/** 초기 질문 생성은 새 Job 또는 진행 Job을 반환하고, 이미 ACTIVE인 세션을 재사용하면 jobId는 null이다. */
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
