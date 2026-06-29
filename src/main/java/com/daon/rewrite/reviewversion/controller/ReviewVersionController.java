package com.daon.rewrite.reviewversion.controller;

import com.daon.rewrite.reviewversion.dto.ReviewVersionDetailResponse;
import com.daon.rewrite.reviewversion.dto.ReviewVersionListResponse;
import com.daon.rewrite.reviewversion.service.ReviewVersionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewVersionController {

    private final ReviewVersionQueryService reviewVersionQueryService;

    @GetMapping("/cover-letters/{coverLetterId}/review-versions")
    public ReviewVersionListResponse findReviewVersions(@PathVariable String coverLetterId) {
        return ReviewVersionListResponse.from(
                reviewVersionQueryService.findMyReviewVersions(coverLetterId)
        );
    }

    @GetMapping("/cover-letters/{coverLetterId}/review-versions/{versionId}")
    public ReviewVersionDetailResponse findReviewVersion(
            @PathVariable String coverLetterId,
            @PathVariable String versionId
    ) {
        return ReviewVersionDetailResponse.from(
                reviewVersionQueryService.findMyReviewVersion(coverLetterId, versionId)
        );
    }
}
