package com.daon.rewrite.llmjob.dto;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.llmjob.entity.LlmJobTargetType;
import com.daon.rewrite.llmjob.entity.LlmJobType;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record LlmJobResponse(
        String id,
        LlmJobType type,
        LlmJobStatus status,
        LlmJobTargetType targetType,
        String targetId,
        ProgressResponse progress,
        int attempt,
        int maxAttempts,
        Object partialResult,
        ResultRefResponse resultRef,
        ErrorResponse error,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static LlmJobResponse from(LlmJob job) {
        return new LlmJobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getTargetType(),
                job.getTargetId(),
                new ProgressResponse(
                        job.getProgressCurrent(),
                        job.getProgressTotal(),
                        job.getProgressMessage()
                ),
                job.getAttempt(),
                job.getMaxAttempts(),
                null,
                ResultRefResponse.from(job),
                ErrorResponse.from(job),
                LocalDateTime.ofInstant(job.getCreatedAt(), API_ZONE),
                job.getCompletedAt() == null ? null : LocalDateTime.ofInstant(job.getCompletedAt(), API_ZONE)
        );
    }

    public record ProgressResponse(
            int current,
            int total,
            String message
    ) {
    }

    public record ResultRefResponse(
            String type,
            String id
    ) {
        private static ResultRefResponse from(LlmJob job) {
            if (job.getResultRefType() == null || job.getResultRefId() == null) {
                return null;
            }
            return new ResultRefResponse(job.getResultRefType().name(), job.getResultRefId());
        }
    }

    public record ErrorResponse(
            String code,
            String message
    ) {
        private static ErrorResponse from(LlmJob job) {
            if (job.getErrorCode() == null) {
                return null;
            }
            return new ErrorResponse(job.getErrorCode(), job.getErrorMessage());
        }
    }
}
