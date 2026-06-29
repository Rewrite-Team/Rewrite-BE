package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.service.ReviewVersionSummary;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record ReviewVersionListItemResponse(
        String id,
        String version,
        boolean isLatest,
        LocalDateTime createdAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static ReviewVersionListItemResponse from(ReviewVersionSummary summary) {
        ReviewVersion reviewVersion = summary.reviewVersion();
        return new ReviewVersionListItemResponse(
                reviewVersion.getId(),
                reviewVersion.getVersion(),
                summary.isLatest(),
                LocalDateTime.ofInstant(reviewVersion.getCreatedAt(), API_ZONE)
        );
    }
}
