package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;

import java.time.Instant;
import java.util.List;

public record SaveFinalAnswersResult(
        String coverLetterId,
        String reviewVersionId,
        List<ReviewVersionQuestionResult> questionResults,
        Instant updatedAt
) {
}
