package com.daon.rewrite.llmjob.dto;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import io.swagger.v3.oas.annotations.media.Schema;

/** 상태 조회 DTO의 공개 정보에 작업 종류를 더한 job.state 이벤트 payload. */
@Schema(requiredProperties = {"jobType", "status", "progress", "resultRef", "error"})
public record LlmJobStateEventResponse(
        LlmJobType jobType,
        com.daon.rewrite.llmjob.entity.LlmJobStatus status,
        LlmJobStateResponse.ProgressResponse progress,
        @Schema(nullable = true)
        LlmJobStateResponse.ResultRefResponse resultRef,
        @Schema(nullable = true)
        LlmJobStateResponse.ErrorResponse error
) {
    public static LlmJobStateEventResponse from(LlmJob job) {
        LlmJobStateResponse state = LlmJobStateResponse.from(job);
        return new LlmJobStateEventResponse(
                job.getType(),
                state.status(),
                state.progress(),
                state.resultRef(),
                state.error()
        );
    }
}
