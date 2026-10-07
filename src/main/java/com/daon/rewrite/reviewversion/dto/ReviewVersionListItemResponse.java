package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.service.ReviewVersionSummary;
import com.daon.rewrite.llmjob.entity.LlmJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 실패한 최신 시도와 편집 가능한 최신 성공 결과를 두 플래그로 구분하는 버전 목록 항목. */
@Schema(requiredProperties = {"id", "version", "status", "isLatest", "isLatestReviewed", "createdAt"})
public record ReviewVersionListItemResponse(
        String id,
        String version,
        LlmJobStatus status,
        boolean isLatest,
        boolean isLatestReviewed,
        LocalDateTime createdAt
) {
    private static final ZoneId API_ZONE = ZoneId.of("Asia/Seoul");

    public static ReviewVersionListItemResponse from(ReviewVersionSummary summary) {
        ReviewVersion reviewVersion = summary.reviewVersion();
        return new ReviewVersionListItemResponse(
                reviewVersion.getId(),
                reviewVersion.getVersion(),
                reviewVersion.getStatus(),
                summary.isLatest(),
                summary.isLatestReviewed(),
                LocalDateTime.ofInstant(reviewVersion.getCreatedAt(), API_ZONE)
        );
    }
}
