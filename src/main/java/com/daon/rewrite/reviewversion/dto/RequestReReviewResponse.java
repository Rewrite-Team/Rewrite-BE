package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import com.daon.rewrite.reviewversion.service.RequestReReviewResult;

public record RequestReReviewResponse(
        String jobId,
        String coverLetterId,
        CoverLetterStatus coverLetterStatus,
        LlmJobStatus jobStatus
) {

    public static RequestReReviewResponse from(RequestReReviewResult result) {
        return new RequestReReviewResponse(
                result.job().getId(),
                result.coverLetterId(),
                CoverLetterStatus.REVIEWED,
                result.job().getStatus()
        );
    }
}
