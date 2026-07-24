package com.daon.rewrite.llmjob.dto;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;

public record LlmJobStateResponse(
        LlmJobStatus status,
        ProgressResponse progress,
        ResultRefResponse resultRef,
        ErrorResponse error
) {
    public static LlmJobStateResponse from(LlmJob job) {
        return new LlmJobStateResponse(
                job.getStatus(),
                new ProgressResponse(job.getProgressCurrent(), job.getProgressTotal(), job.getProgressMessage()),
                ResultRefResponse.from(job),
                ErrorResponse.from(job)
        );
    }

    public record ProgressResponse(int current, int total, String message) {
    }

    public record ResultRefResponse(String type, String id) {
        private static ResultRefResponse from(LlmJob job) {
            if (job.getResultRefType() == null || job.getResultRefId() == null) {
                return null;
            }
            return new ResultRefResponse(job.getResultRefType().name(), job.getResultRefId());
        }
    }

    public record ErrorResponse(String code, String message) {
        private static ErrorResponse from(LlmJob job) {
            if (job.getErrorCode() == null) {
                return null;
            }
            return new ErrorResponse(publicErrorCode(job.getErrorCode()), job.getErrorMessage());
        }

        private static String publicErrorCode(String errorCode) {
            return switch (errorCode) {
                case "LLM_CONTEXT_LENGTH_EXCEEDED", "LLM_CONTENT_FILTERED" -> errorCode;
                default -> "LLM_PROVIDER_ERROR";
            };
        }
    }
}
