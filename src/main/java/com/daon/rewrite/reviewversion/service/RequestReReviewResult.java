package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.llmjob.entity.LlmJob;

public record RequestReReviewResult(
        String coverLetterId,
        LlmJob job
) {
}
