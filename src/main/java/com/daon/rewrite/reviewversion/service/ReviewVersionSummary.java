package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;

public record ReviewVersionSummary(
        ReviewVersion reviewVersion,
        boolean isLatest
) {
}
