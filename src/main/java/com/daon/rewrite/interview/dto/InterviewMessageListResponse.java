package com.daon.rewrite.interview.dto;

import com.daon.rewrite.interview.entity.InterviewMessageRole;
import com.daon.rewrite.interview.service.InterviewMessageItemResult;
import com.daon.rewrite.interview.service.InterviewMessageListResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 저장된 메시지의 표시 문장·점수와 최신 사용자 답변의 미해결 피드백 Job을 반환한다.
 * ASSISTANT의 content에는 피드백·꼬리질문이 연결되어 있으며 내부 분석 필드는 응답에 포함하지 않는다.
 */
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
