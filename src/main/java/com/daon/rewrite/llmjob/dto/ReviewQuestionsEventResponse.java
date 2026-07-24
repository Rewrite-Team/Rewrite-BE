package com.daon.rewrite.llmjob.dto;

import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;

import java.util.List;

public record ReviewQuestionsEventResponse(List<QuestionResponse> items) {

    public ReviewQuestionsEventResponse {
        items = List.copyOf(items);
    }

    public static ReviewQuestionsEventResponse from(List<ReviewJobQuestionResult> results) {
        return new ReviewQuestionsEventResponse(results.stream()
                .map(QuestionResponse::from)
                .toList());
    }

    public record QuestionResponse(
            String questionId,
            int order,
            String aiReport,
            String rewrittenAnswer,
            int rewrittenAnswerLength,
            String finalAnswer,
            int finalAnswerLength
    ) {
        private static QuestionResponse from(ReviewJobQuestionResult result) {
            return new QuestionResponse(
                    result.getQuestion().getId(),
                    result.getQuestionOrder(),
                    result.getAiReport(),
                    result.getRewrittenAnswer(),
                    result.getRewrittenAnswerLength(),
                    result.getFinalAnswer(),
                    result.getFinalAnswerLength()
            );
        }
    }
}
