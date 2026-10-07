package com.daon.rewrite.llmjob.dto;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Job 엔티티를 상태 복구용 공개 정보로 변환한다. SSE 상태 이벤트도 같은 진행률·결과·오류 변환을 사용한다. */
@Schema(requiredProperties = {"status", "progress", "resultRef", "error"})
public record LlmJobStateResponse(
        LlmJobStatus status,
        ProgressResponse progress,
        @Schema(nullable = true)
        ResultRefResponse resultRef,
        @Schema(nullable = true)
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

    @Schema(name = "LlmJobProgressResponse", requiredProperties = {"current", "total", "message"})
    public record ProgressResponse(int current, int total, String message) {
    }

    @Schema(requiredProperties = {"type", "id"})
    public record ResultRefResponse(String type, String id) {
        private static ResultRefResponse from(LlmJob job) {
            if (job.getResultRefType() == null || job.getResultRefId() == null) {
                return null;
            }
            return new ResultRefResponse(job.getResultRefType().name(), job.getResultRefId());
        }
    }

    @Schema(name = "LlmJobErrorResponse", requiredProperties = {"code", "message"})
    public record ErrorResponse(String code, String message) {
        private static ErrorResponse from(LlmJob job) {
            if (job.getErrorCode() == null) {
                return null;
            }
            return new ErrorResponse(publicErrorCode(job.getErrorCode()), job.getErrorMessage());
        }

        private static String publicErrorCode(String errorCode) {
            // 화면에서 구별하는 두 오류 외의 내부 실패 분류는 공통 provider 오류 코드로 공개한다.
            return switch (errorCode) {
                case "LLM_CONTEXT_LENGTH_EXCEEDED", "LLM_CONTENT_FILTERED" -> errorCode;
                default -> "LLM_PROVIDER_ERROR";
            };
        }
    }
}
