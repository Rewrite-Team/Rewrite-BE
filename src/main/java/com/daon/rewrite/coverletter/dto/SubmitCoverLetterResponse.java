package com.daon.rewrite.coverletter.dto;

import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.service.SubmitCoverLetterResult;

public record SubmitCoverLetterResponse(
        String coverLetterId,
        CoverLetterStatus status,
        String jobId,
        String latestReviewVersionId
) {

    public static SubmitCoverLetterResponse from(SubmitCoverLetterResult result) {
        return new SubmitCoverLetterResponse(
                result.coverLetter().getId(),
                result.coverLetter().getStatus(),
                result.job() == null ? null : result.job().getId(),
                result.coverLetter().getLatestReviewVersionId()
        );
    }
}
