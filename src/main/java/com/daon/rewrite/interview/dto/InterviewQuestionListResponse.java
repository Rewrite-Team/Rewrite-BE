package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.service.InterviewQuestionItemResult;
import com.daon.rewrite.interview.service.InterviewQuestionListResult;

import java.util.List;

public record InterviewQuestionListResponse(
        List<InterviewQuestionResponse> items,
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
