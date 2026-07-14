package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.service.InterviewQuestionItemResult;
import com.daon.rewrite.interview.service.InterviewQuestionListResult;

import java.util.List;

public record InterviewQuestionListResponse(
        String interviewSessionId,
        List<InterviewQuestionResponse> items
) {

    public static InterviewQuestionListResponse from(InterviewQuestionListResult result) {
        return new InterviewQuestionListResponse(
                result.interviewSessionId(),
                result.items().stream()
                        .map(InterviewQuestionResponse::from)
                        .toList()
        );
    }

    public record InterviewQuestionResponse(
            String id,
            String sourceReviewVersionId,
            int order,
            String question,
            String threadId
    ) {

        private static InterviewQuestionResponse from(InterviewQuestionItemResult item) {
            return new InterviewQuestionResponse(
                    item.id(),
                    item.sourceReviewVersionId(),
                    item.order(),
                    item.question(),
                    item.threadId()
            );
        }
    }
}
