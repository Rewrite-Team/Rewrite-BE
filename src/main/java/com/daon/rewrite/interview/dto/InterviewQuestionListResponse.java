package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.service.InterviewQuestionItemResult;
import com.daon.rewrite.interview.service.InterviewQuestionListResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 최신 질문부터의 목록과 다음 조회용 cursor다. threadId는 각 질문과 함께 생성된 대화방을 가리킨다. */
@Schema(requiredProperties = {"items", "nextCursor"})
public record InterviewQuestionListResponse(
        List<InterviewQuestionResponse> items,
        @Schema(nullable = true)
        String nextCursor
) {

    public static InterviewQuestionListResponse from(InterviewQuestionListResult result) {
        return new InterviewQuestionListResponse(
                result.items().stream()
                        .map(InterviewQuestionResponse::from)
                        .toList(),
                result.nextCursor()
        );
    }

    @Schema(requiredProperties = {"id", "order", "question", "threadId"})
    public record InterviewQuestionResponse(
            String id,
            int order,
            String question,
            String threadId
    ) {

        private static InterviewQuestionResponse from(InterviewQuestionItemResult item) {
            return new InterviewQuestionResponse(
                    item.id(),
                    item.order(),
                    item.question(),
                    item.threadId()
            );
        }
    }
}
