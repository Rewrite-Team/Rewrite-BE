package com.daon.rewrite.reviewversion.dto;

import com.daon.rewrite.reviewversion.service.ReviewVersionSummary;

import java.util.List;

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
