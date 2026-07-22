package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.reviewversion.service.RequestReReviewResult;

public record RequestReReviewResponse(
        CoverLetterStatus displayStatus,
        String jobId
) {

    public static RequestReReviewResponse from(RequestReReviewResult result) {
        return new RequestReReviewResponse(
                result.coverLetter().getStatus(),
                result.job().getId()
        );
    }
}
