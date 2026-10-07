package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.service.SubmitCoverLetterResult;
import io.swagger.v3.oas.annotations.media.Schema;

/** 첨삭 완료를 기다리지 않는 시작 응답. 이미 최초 첨삭이 완료됐으면 새 Job 없이 jobId가 null이다. */
@Schema(requiredProperties = {"displayStatus", "jobId"})
public record SubmitCoverLetterResponse(
        CoverLetterStatus displayStatus,
        @Schema(nullable = true)
        String jobId
) {

    public static SubmitCoverLetterResponse from(SubmitCoverLetterResult result) {
        return new SubmitCoverLetterResponse(
                result.coverLetter().getStatus(),
                result.job() == null ? null : result.job().getId()
        );
    }
}
