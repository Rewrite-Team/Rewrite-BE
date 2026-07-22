package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.service.CoverLetterDetailResult;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record CoverLetterDetailResponse(
        CoverLetterResponse coverLetter,
        ReviewVersionResponse reviewVersion,
        ReviewJobResponse reviewJob,
        List<QuestionResponse> questions
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static CoverLetterDetailResponse from(CoverLetterDetailResult result) {
        return new CoverLetterDetailResponse(
                CoverLetterResponse.from(result.coverLetter()),
                ReviewVersionResponse.from(result.reviewVersion()),
                ReviewJobResponse.from(result.reviewJob()),
                result.questions().stream().map(QuestionResponse::from).toList()
        );
    }

    public record CoverLetterResponse(
            String id,
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl,
            String preferences,
            CoverLetterStatus displayStatus
    ) {
        private static CoverLetterResponse from(CoverLetter coverLetter) {
            return new CoverLetterResponse(
                    coverLetter.getId(),
                    coverLetter.getTitle(),
                    coverLetter.getCompanyName(),
                    coverLetter.getPositionTitle(),
                    coverLetter.getJobPostingUrl(),
                    coverLetter.getPreferences(),
                    coverLetter.getStatus()
            );
        }
    }

    public record ReviewVersionResponse(
            String id,
            String version,
            boolean isLatest,
            String requestInstruction,
            LocalDateTime createdAt
    ) {
        private static ReviewVersionResponse from(CoverLetterDetailResult.ReviewVersionResult result) {
            if (result == null) {
                return null;
            }
            ReviewVersion reviewVersion = result.value();
            return new ReviewVersionResponse(
                    reviewVersion.getId(),
                    reviewVersion.getVersion(),
                    result.latest(),
                    reviewVersion.getRequestInstruction(),
                    LocalDateTime.ofInstant(reviewVersion.getCreatedAt(), API_ZONE)
            );
        }
    }

    public record ReviewJobResponse(
            String id,
            LlmJobStatus status,
            ProgressResponse progress,
            ErrorResponse error
    ) {
        private static ReviewJobResponse from(LlmJob job) {
            if (job == null) {
                return null;
            }
            return new ReviewJobResponse(
                    job.getId(),
                    job.getStatus(),
                    new ProgressResponse(job.getProgressCurrent(), job.getProgressTotal(), job.getProgressMessage()),
                    ErrorResponse.from(job)
            );
        }
    }

    public record ProgressResponse(int current, int total, String message) {
    }

    public record ErrorResponse(String code, String message) {
        private static ErrorResponse from(LlmJob job) {
            if (job.getErrorCode() == null) {
                return null;
            }
            String code = switch (job.getErrorCode()) {
                case "LLM_CONTEXT_LENGTH_EXCEEDED", "LLM_CONTENT_FILTERED" -> job.getErrorCode();
                default -> "LLM_PROVIDER_ERROR";
            };
            return new ErrorResponse(code, job.getErrorMessage());
        }
    }

    public record QuestionResponse(
            String questionResultId,
            String questionId,
            int order,
            String question,
            Integer maxAnswerLength,
            String originalAnswer,
            Integer originalAnswerLength,
            String aiReport,
            String rewrittenAnswer,
            Integer rewrittenAnswerLength,
            String finalAnswer,
            Integer finalAnswerLength
    ) {
        private static QuestionResponse from(CoverLetterDetailResult.QuestionResult result) {
            return new QuestionResponse(
                    result.questionResultId(),
                    result.questionId(),
                    result.order(),
                    result.question(),
                    result.maxAnswerLength(),
                    result.originalAnswer(),
                    result.originalAnswerLength(),
                    result.aiReport(),
                    result.rewrittenAnswer(),
                    result.rewrittenAnswerLength(),
                    result.finalAnswer(),
                    result.finalAnswerLength()
            );
        }
    }
}
