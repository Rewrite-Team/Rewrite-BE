package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.service.SubmitCoverLetterResult;
import io.swagger.v3.oas.annotations.media.Schema;

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
