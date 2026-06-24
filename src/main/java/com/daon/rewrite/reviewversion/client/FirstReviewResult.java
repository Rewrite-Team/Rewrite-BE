package com.daon.rewrite.reviewversion.client;

public record FirstReviewResult(
        String questionId,
        String aiReport,
        String rewrittenAnswer
) {
}
