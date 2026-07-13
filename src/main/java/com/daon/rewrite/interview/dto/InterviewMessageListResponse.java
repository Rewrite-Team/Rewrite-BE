package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.entity.InterviewMessageRole;
import com.daon.rewrite.interview.service.InterviewMessageItemResult;
import com.daon.rewrite.interview.service.InterviewMessageListResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record InterviewMessageListResponse(
        String threadId,
        List<InterviewMessageResponse> items
) {

    public static InterviewMessageListResponse from(InterviewMessageListResult result) {
        return new InterviewMessageListResponse(
                result.threadId(),
                result.items().stream()
                        .map(InterviewMessageResponse::from)
                        .toList()
        );
    }

    public record InterviewMessageResponse(
            String id,
            InterviewMessageRole role,
            String content,
            FeedbackResponse feedback,
            Integer score,
            String followUpQuestion,
            LocalDateTime createdAt
    ) {
        private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

        private static InterviewMessageResponse from(InterviewMessageItemResult item) {
            return new InterviewMessageResponse(
                    item.id(),
                    item.role(),
                    item.content(),
                    FeedbackResponse.from(item),
                    item.score(),
                    item.followUpQuestion(),
                    LocalDateTime.ofInstant(item.createdAt(), API_ZONE)
            );
        }
    }

    public record FeedbackResponse(
            String summary,
            List<String> strengths,
            List<String> improvements
    ) {

        private static FeedbackResponse from(InterviewMessageItemResult item) {
            if (item.feedbackSummary() == null) {
                return null;
            }
            return new FeedbackResponse(
                    item.feedbackSummary(),
                    item.feedbackStrengths(),
                    item.feedbackImprovements()
            );
        }
    }
}
