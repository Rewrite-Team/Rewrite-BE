package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;

public record ReviewVersionQuestionResultResponse(
        String questionResultId,
        String questionId,
        int order,
        String question,
        int maxAnswerLength,
        String originalAnswer,
        int originalAnswerLength,
        String aiReport,
        String rewrittenAnswer,
        int rewrittenAnswerLength,
        String finalAnswer,
        int finalAnswerLength
) {

    public static ReviewVersionQuestionResultResponse from(ReviewVersionQuestionResult questionResult) {
        return new ReviewVersionQuestionResultResponse(
                questionResult.getId(),
                questionResult.getQuestion().getId(),
                questionResult.getQuestionOrder(),
                questionResult.getQuestionText(),
                questionResult.getMaxAnswerLength(),
                questionResult.getOriginalAnswer(),
                questionResult.getOriginalAnswerLength(),
                questionResult.getAiReport(),
                questionResult.getRewrittenAnswer(),
                questionResult.getRewrittenAnswerLength(),
                questionResult.getFinalAnswer(),
                questionResult.getFinalAnswerLength()
        );
    }
}
