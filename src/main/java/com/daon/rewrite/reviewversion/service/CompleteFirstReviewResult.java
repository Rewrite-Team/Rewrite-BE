package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;

import java.util.List;

public record CompleteFirstReviewResult(
        ReviewVersion reviewVersion,
        List<ReviewVersionQuestionResult> questionResults
) {

    public CompleteFirstReviewResult {
        questionResults = List.copyOf(questionResults);
    }
}
