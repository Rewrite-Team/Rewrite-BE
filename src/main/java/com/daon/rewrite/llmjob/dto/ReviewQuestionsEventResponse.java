package com.daon.rewrite.llmjob.dto;

import com.daon.rewrite.reviewversion.entity.ReviewJobQuestionResult;

import java.util.List;

/**
 * 연결 시 완료 문항 스냅샷과 새 문항 완료 이벤트가 함께 사용하는 payload.
 * items는 스냅샷이면 전체 완료 문항, 변경이면 단건이며 클라이언트는 questionId로 같은 문항을 갱신한다.
 */
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
