package com.daon.rewrite.reviewversion.service;

public record ReviewQuestionResultInput(
        String questionId,
        String aiReport,
        String rewrittenAnswer
) {
}
