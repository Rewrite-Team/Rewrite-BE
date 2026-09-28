package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.entity.InterviewMessageRole;
import com.daon.rewrite.interview.service.InterviewMessageItemResult;
import com.daon.rewrite.interview.service.InterviewMessageListResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Schema(requiredProperties = {"jobId", "items"})
public record InterviewMessageListResponse(
        @Schema(nullable = true)
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

    @Schema(requiredProperties = {"id", "role", "content", "score", "createdAt"})
    public record InterviewMessageResponse(
            String id,
            InterviewMessageRole role,
            String content,
            @Schema(nullable = true)
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
