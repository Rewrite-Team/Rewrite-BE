package com.daon.rewrite.interview.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.service.CurrentInterviewResult;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record CurrentInterviewResponse(
        CoverLetterResponse coverLetter,
        InterviewSessionResponse interviewSession
) {

    public static CurrentInterviewResponse from(CurrentInterviewResult result) {
        return new CurrentInterviewResponse(
                CoverLetterResponse.from(result.coverLetter()),
                InterviewSessionResponse.from(
                        result.interviewSession(),
                        result.job() == null ? null : result.job().getId()
                )
        );
    }

    public record CoverLetterResponse(
            String id,
            String title,
            String companyName,
            String positionTitle
    ) {
        private static CoverLetterResponse from(CoverLetter coverLetter) {
            return new CoverLetterResponse(
                    coverLetter.getId(),
                    coverLetter.getTitle(),
                    coverLetter.getCompanyName(),
                    coverLetter.getPositionTitle()
            );
        }
    }

    public record InterviewSessionResponse(
            String id,
            String initialSourceReviewVersionId,
            InterviewSessionStatus status,
            String jobId,
            LocalDateTime createdAt
    ) {
        private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

        private static InterviewSessionResponse from(InterviewSession interviewSession, String jobId) {
            if (interviewSession == null) {
                return null;
            }
            return new InterviewSessionResponse(
                    interviewSession.getId(),
                    interviewSession.getInitialSourceReviewVersionId(),
                    interviewSession.getStatus(),
                    jobId,
                    LocalDateTime.ofInstant(interviewSession.getCreatedAt(), API_ZONE)
            );
        }
    }
}
