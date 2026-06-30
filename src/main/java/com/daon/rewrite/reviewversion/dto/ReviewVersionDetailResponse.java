package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.service.ReviewVersionDetail;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record ReviewVersionDetailResponse(
        String id,
        String coverLetterId,
        String version,
        boolean isLatest,
        String requestInstruction,
        LocalDateTime createdAt,
        List<ReviewVersionQuestionResultResponse> questionResults
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static ReviewVersionDetailResponse from(ReviewVersionDetail detail) {
        ReviewVersion reviewVersion = detail.reviewVersion();
        return new ReviewVersionDetailResponse(
                reviewVersion.getId(),
                detail.coverLetterId(),
                reviewVersion.getVersion(),
                detail.isLatest(),
                reviewVersion.getRequestInstruction(),
                LocalDateTime.ofInstant(reviewVersion.getCreatedAt(), API_ZONE),
                detail.questionResults().stream()
                        .map(ReviewVersionQuestionResultResponse::from)
                        .toList()
        );
    }
}
