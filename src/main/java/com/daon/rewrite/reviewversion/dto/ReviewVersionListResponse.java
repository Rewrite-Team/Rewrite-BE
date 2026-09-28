package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.reviewversion.service.ReviewVersionSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(requiredProperties = "items")
public record ReviewVersionListResponse(
        List<ReviewVersionListItemResponse> items
) {

    public static ReviewVersionListResponse from(List<ReviewVersionSummary> summaries) {
        return new ReviewVersionListResponse(
                summaries.stream()
                        .map(ReviewVersionListItemResponse::from)
                        .toList()
        );
    }
}
