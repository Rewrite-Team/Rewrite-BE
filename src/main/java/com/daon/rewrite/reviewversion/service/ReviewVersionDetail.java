package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;

import java.util.List;

public record ReviewVersionDetail(
        String coverLetterId,
        ReviewVersion reviewVersion,
        boolean isLatest,
        List<ReviewVersionQuestionResult> questionResults
) {
}
