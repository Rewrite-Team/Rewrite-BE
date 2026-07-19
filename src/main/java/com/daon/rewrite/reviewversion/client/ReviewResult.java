package com.daon.rewrite.reviewversion.client;

public record ReviewResult(
        String questionId,
        String aiReport,
        String rewrittenAnswer
) {
}
