package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.llmjob.entity.LlmJob;

public record RequestReReviewResult(
        CoverLetter coverLetter,
        LlmJob job
) {
}
