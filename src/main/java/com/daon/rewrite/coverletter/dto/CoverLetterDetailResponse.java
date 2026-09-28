package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.service.CoverLetterDetailResult;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Schema(requiredProperties = {"coverLetter", "reviewVersion", "reviewJob", "questions"})
public record CoverLetterDetailResponse(
        CoverLetterResponse coverLetter,
        @Schema(nullable = true)
        ReviewVersionResponse reviewVersion,
        @Schema(nullable = true)
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

    @Schema(name = "CoverLetterDetailCoverLetterResponse", requiredProperties = {"id", "title", "companyName", "positionTitle", "jobPostingUrl", "preferences", "displayStatus"})
    public record CoverLetterResponse(
            String id,
            @Schema(nullable = true)
            String title,
            @Schema(nullable = true)
            String companyName,
            @Schema(nullable = true)
            String positionTitle,
            @Schema(nullable = true)
            String jobPostingUrl,
            @Schema(nullable = true)
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

    @Schema(name = "CoverLetterDetailReviewVersionResponse", requiredProperties = {"id", "version", "isLatest", "requestInstruction", "createdAt"})
    public record ReviewVersionResponse(
            String id,
            String version,
            boolean isLatest,
            @Schema(nullable = true)
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

    @Schema(requiredProperties = {"id", "status", "progress", "error"})
    public record ReviewJobResponse(
            String id,
            LlmJobStatus status,
            ProgressResponse progress,
            @Schema(nullable = true)
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

    @Schema(name = "CoverLetterDetailProgressResponse", requiredProperties = {"current", "total", "message"})
    public record ProgressResponse(int current, int total, String message) {
    }

    @Schema(name = "CoverLetterDetailJobErrorResponse", requiredProperties = {"code", "message"})
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

    @Schema(requiredProperties = {"questionResultId", "questionId", "order", "question", "maxAnswerLength", "originalAnswer", "originalAnswerLength", "aiReport", "rewrittenAnswer", "rewrittenAnswerLength", "finalAnswer", "finalAnswerLength"})
    public record QuestionResponse(
            @Schema(nullable = true)
            String questionResultId,
            String questionId,
            int order,
            @Schema(nullable = true)
            String question,
            @Schema(nullable = true)
            Integer maxAnswerLength,
            @Schema(nullable = true)
            String originalAnswer,
            @Schema(nullable = true)
            Integer originalAnswerLength,
            @Schema(nullable = true)
            String aiReport,
            @Schema(nullable = true)
            String rewrittenAnswer,
            @Schema(nullable = true)
            Integer rewrittenAnswerLength,
            @Schema(nullable = true)
            String finalAnswer,
            @Schema(nullable = true)
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
