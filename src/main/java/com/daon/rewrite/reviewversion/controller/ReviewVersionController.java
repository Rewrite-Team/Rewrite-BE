package com.daon.rewrite.reviewversion.controller;

import com.daon.rewrite.reviewversion.dto.ReviewVersionDetailResponse;
import com.daon.rewrite.reviewversion.dto.ReviewVersionListResponse;
import com.daon.rewrite.reviewversion.dto.RequestReReviewRequest;
import com.daon.rewrite.reviewversion.dto.RequestReReviewResponse;
import com.daon.rewrite.reviewversion.dto.SaveFinalAnswersRequest;
import com.daon.rewrite.reviewversion.dto.SaveFinalAnswersResponse;
import com.daon.rewrite.reviewversion.service.ReviewVersionCommandService;
import com.daon.rewrite.reviewversion.service.ReviewVersionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewVersionController {

    private final ReviewVersionQueryService reviewVersionQueryService;
    private final ReviewVersionCommandService reviewVersionCommandService;

    @GetMapping("/cover-letters/{coverLetterId}/review-versions")
    public ReviewVersionListResponse findReviewVersions(@PathVariable String coverLetterId) {
        return ReviewVersionListResponse.from(
                reviewVersionQueryService.findMyReviewVersions(coverLetterId)
        );
    }

    @PostMapping("/cover-letters/{coverLetterId}/review-versions")
    public RequestReReviewResponse requestReReview(
            @PathVariable String coverLetterId,
            @RequestBody(required = false) RequestReReviewRequest request
    ) {
        return RequestReReviewResponse.from(
                reviewVersionCommandService.requestMyReReview(
                        coverLetterId,
                        request == null ? null : request.requestInstruction()
                )
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

    @PutMapping("/cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers")
    public SaveFinalAnswersResponse saveFinalAnswers(
            @PathVariable String coverLetterId,
            @PathVariable String versionId,
            @RequestBody SaveFinalAnswersRequest request
    ) {
        reviewVersionCommandService.saveMyFinalAnswers(
                coverLetterId,
                versionId,
                request == null ? null : request.toInputs()
        );
        return SaveFinalAnswersResponse.completed();
    }
}
