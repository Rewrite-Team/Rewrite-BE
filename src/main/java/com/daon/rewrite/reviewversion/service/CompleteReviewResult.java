package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;

import java.util.List;

public record CompleteReviewResult(
        ReviewVersion reviewVersion,
        List<ReviewVersionQuestionResult> questionResults
) {

    public CompleteReviewResult {
        questionResults = List.copyOf(questionResults);
    }
}
