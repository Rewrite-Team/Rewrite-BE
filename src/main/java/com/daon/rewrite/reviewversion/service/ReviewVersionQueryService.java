package com.daon.rewrite.reviewversion.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ReviewVersionQueryService {

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;

    @Transactional(readOnly = true)
    public List<ReviewVersionSummary> findMyReviewVersions(String coverLetterId) {
        CoverLetter coverLetter = findMyActiveCoverLetter(coverLetterId);
        List<ReviewVersion> versions = reviewVersionRepository.findByCoverLetterIdOrderByCreatedAtAsc(coverLetter.getId());
        versions.sort(Comparator.comparing(ReviewVersion::getCreatedAt)
                .thenComparingInt(version -> Integer.parseInt(version.getVersion().substring(3))));
        String latestVersion = "v0." + versions.size();
        return versions
                .stream()
                .map(reviewVersion -> new ReviewVersionSummary(
                        reviewVersion,
                        reviewVersion.getVersion().equals(latestVersion),
                        reviewVersion.getId().equals(coverLetter.getLatestReviewedVersionId())
                ))
                .toList();
    }

    private CoverLetter findMyActiveCoverLetter(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        return coverLetterRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

}
