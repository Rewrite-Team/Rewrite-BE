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

/**
 * 삭제되지 않은 자기소개서의 소유자를 확인하고 첨삭 시도 이력을 생성 순서대로 조회한다.
 * 최신 시도와 최신 성공 결과를 따로 표시해 실패한 새 시도가 기존 성공 결과를 대체한 것으로 보이지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class ReviewVersionQueryService {

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final ReviewVersionRepository reviewVersionRepository;

    /**
     * 같은 생성 시각의 버전은 표시 번호로 정렬하며 실패·취소 상태로 버전을 걸러내지 않는다.
     * isLatest는 가장 최근 시도, isLatestReviewed는 자기소개서가 가리키는 최신 성공 버전이다.
     */
    @Transactional(readOnly = true)
    public List<ReviewVersionSummary> findMyReviewVersions(String coverLetterId) {
        CoverLetter coverLetter = findMyActiveCoverLetter(coverLetterId);
        List<ReviewVersion> versions = reviewVersionRepository.findByCoverLetterIdOrderByCreatedAtAsc(coverLetter.getId());
        versions.sort(Comparator.comparing(ReviewVersion::getCreatedAt)
                .thenComparingLong(ReviewVersion::getVersionNumber));
        long latestVersionNumber = versions.stream()
                .mapToLong(ReviewVersion::getVersionNumber)
                .max()
                .orElse(0);
        return versions
                .stream()
                .map(reviewVersion -> new ReviewVersionSummary(
                        reviewVersion,
                        reviewVersion.getVersionNumber() == latestVersionNumber,
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
