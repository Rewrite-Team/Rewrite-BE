package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.entity.InterviewMessageRole;
import com.daon.rewrite.interview.service.InterviewMessageItemResult;
import com.daon.rewrite.interview.service.InterviewMessageListResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record InterviewMessageListResponse(
        String jobId,
        List<InterviewMessageResponse> items
) {

    public static InterviewMessageListResponse from(InterviewMessageListResult result) {
        return new InterviewMessageListResponse(
                result.jobId(),
                result.items().stream()
                        .map(InterviewMessageResponse::from)
                        .toList()
        );
    }

    public record InterviewMessageResponse(
            String id,
            InterviewMessageRole role,
            String content,
            Integer score,
            LocalDateTime createdAt
    ) {
        private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

        private static InterviewMessageResponse from(InterviewMessageItemResult item) {
            return new InterviewMessageResponse(
                    item.id(),
                    item.role(),
                    item.content(),
                    item.score(),
                    LocalDateTime.ofInstant(item.createdAt(), API_ZONE)
            );
        }
    }
}
