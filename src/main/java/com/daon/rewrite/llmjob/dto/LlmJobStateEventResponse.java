package com.daon.rewrite.llmjob.dto;

import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobType;

/** 상태 조회 DTO의 공개 정보에 작업 종류를 더한 job.state 이벤트 payload. */
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
