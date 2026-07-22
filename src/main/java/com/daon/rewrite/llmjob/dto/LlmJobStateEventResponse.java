package com.daon.rewrite.llmjob.dto;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobType;

public record LlmJobStateEventResponse(
        LlmJobType jobType,
        com.daon.rewrite.llmjob.entity.LlmJobStatus status,
        LlmJobStateResponse.ProgressResponse progress,
        LlmJobStateResponse.ResultRefResponse resultRef,
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
