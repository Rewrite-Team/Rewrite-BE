package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.service.CurrentInterviewResult;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record CurrentInterviewResponse(
        String coverLetterId,
        InterviewSessionResponse interviewSession
) {

    public static CurrentInterviewResponse from(CurrentInterviewResult result) {
        return new CurrentInterviewResponse(
                result.coverLetterId(),
                InterviewSessionResponse.from(result.interviewSession())
        );
    }

    public record InterviewSessionResponse(
            String id,
            String initialSourceReviewVersionId,
            InterviewSessionStatus status,
            LocalDateTime createdAt
    ) {
        private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

        private static InterviewSessionResponse from(InterviewSession interviewSession) {
            if (interviewSession == null) {
                return null;
            }
            return new InterviewSessionResponse(
                    interviewSession.getId(),
                    interviewSession.getInitialSourceReviewVersionId(),
                    interviewSession.getStatus(),
                    LocalDateTime.ofInstant(interviewSession.getCreatedAt(), API_ZONE)
            );
        }
    }
}
