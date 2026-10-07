package com.daon.rewrite.interview.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.interview.entity.InterviewSessionStatus;
import com.daon.rewrite.interview.service.CurrentInterviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 자기소개서 요약은 항상 포함하고, 아직 면접을 시작하지 않았으면 interviewSession을 null로 반환한다. */
@Schema(requiredProperties = {"coverLetter", "interviewSession"})
public record CurrentInterviewResponse(
        CoverLetterResponse coverLetter,
        @Schema(nullable = true)
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

    @Schema(name = "CurrentInterviewCoverLetterResponse", requiredProperties = {"id", "title", "companyName", "positionTitle"})
    public record CoverLetterResponse(
            String id,
            @Schema(nullable = true)
            String title,
            @Schema(nullable = true)
            String companyName,
            @Schema(nullable = true)
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

    /**
     * 세션 상태와 복구할 질문 생성 Job을 함께 표현한다. 추가 질문 생성·실패 중에도 세션은 ACTIVE일 수 있다.
     * jobId의 선택은 조회 서비스가 담당하며 답변 피드백 Job은 포함하지 않는다.
     */
    @Schema(requiredProperties = {"id", "initialSourceReviewVersionId", "status", "jobId", "createdAt"})
    public record InterviewSessionResponse(
            String id,
            String initialSourceReviewVersionId,
            InterviewSessionStatus status,
            @Schema(nullable = true)
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
