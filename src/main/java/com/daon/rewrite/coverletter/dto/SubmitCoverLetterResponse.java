package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.service.SubmitCoverLetterResult;

public record SubmitCoverLetterResponse(
        CoverLetterStatus displayStatus,
        String jobId
) {

    public static SubmitCoverLetterResponse from(SubmitCoverLetterResult result) {
        return new SubmitCoverLetterResponse(
                result.coverLetter().getStatus(),
                result.job() == null ? null : result.job().getId()
        );
    }
}
