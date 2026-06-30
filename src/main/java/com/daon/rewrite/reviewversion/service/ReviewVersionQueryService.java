package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.entity.ReviewVersionQuestionResult;
import com.daon.rewrite.reviewversion.repository.ReviewVersionQuestionResultRepository;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewVersionQueryService {

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final ReviewVersionQuestionResultRepository questionResultRepository;

    @Transactional(readOnly = true)
    public List<ReviewVersionSummary> findMyReviewVersions(String coverLetterId) {
        CoverLetter coverLetter = findMyActiveCoverLetter(coverLetterId);
        return reviewVersionRepository.findByCoverLetterIdOrderByCreatedAtDesc(coverLetter.getId())
                .stream()
                .map(reviewVersion -> new ReviewVersionSummary(
                        reviewVersion,
                        isLatest(coverLetter, reviewVersion)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewVersionDetail findMyReviewVersion(String coverLetterId, String versionId) {
        CoverLetter coverLetter = findMyActiveCoverLetter(coverLetterId);
        ReviewVersion reviewVersion = reviewVersionRepository.findByIdAndCoverLetterId(versionId, coverLetter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<ReviewVersionQuestionResult> questionResults = questionResultRepository
                .findByReviewVersionIdOrderByQuestionOrderAsc(reviewVersion.getId());

        return new ReviewVersionDetail(
                coverLetter.getId(),
                reviewVersion,
                isLatest(coverLetter, reviewVersion),
                questionResults
        );
    }

    private CoverLetter findMyActiveCoverLetter(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        return coverLetterRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private boolean isLatest(CoverLetter coverLetter, ReviewVersion reviewVersion) {
        return reviewVersion.getId().equals(coverLetter.getLatestReviewVersionId());
    }
}
