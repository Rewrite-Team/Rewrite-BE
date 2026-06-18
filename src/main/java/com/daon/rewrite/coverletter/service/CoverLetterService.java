package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.response.ErrorResponse;
import com.daon.rewrite.global.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CoverLetterService {

    private static final String COVER_LETTER_ID_PREFIX = "cl";
    private static final int MAX_LIST_SIZE = 9;
    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MAX_COMPANY_NAME_LENGTH = 30;
    private static final int MAX_POSITION_TITLE_LENGTH = 30;
    private static final int MAX_JOB_POSTING_URL_LENGTH = 500;

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @Transactional
    public CoverLetter createDraft() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter draft = CoverLetter.draft(
                idGenerator.generate(COVER_LETTER_ID_PREFIX),
                currentUser.id(),
                Instant.now(clock)
        );

        return coverLetterRepository.save(draft);
    }

    @Transactional(readOnly = true)
    public Page<CoverLetter> findMyCoverLetters(int page, int size, CoverLetterStatus status) {
        validateListQuery(page, size);

        CurrentUser currentUser = currentUserProvider.currentUser();
        Pageable pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        if (status == null) {
            return coverLetterRepository.findByOwnerIdAndDeletedAtIsNull(currentUser.id(), pageable);
        }

        return coverLetterRepository.findByOwnerIdAndStatusAndDeletedAtIsNull(
                currentUser.id(),
                status,
                pageable
        );
    }

    @Transactional
    public CoverLetter deleteMyCoverLetter(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        coverLetter.markDeleted(Instant.now(clock));
        return coverLetter;
    }

    @Transactional
    public CoverLetter saveBasicInfo(
            String coverLetterId,
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl
    ) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (coverLetter.getStatus() != CoverLetterStatus.DRAFT) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_DRAFT);
        }

        BasicInfoInput input = validateAndNormalizeBasicInfo(
                title,
                companyName,
                positionTitle,
                jobPostingUrl
        );
        coverLetter.fillBasicInfo(
                input.title(),
                input.companyName(),
                input.positionTitle(),
                input.jobPostingUrl(),
                Instant.now(clock)
        );

        return coverLetter;
    }

    private void validateListQuery(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_LIST_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private BasicInfoInput validateAndNormalizeBasicInfo(
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl
    ) {
        List<ErrorResponse.ErrorDetail> details = new ArrayList<>();
        String normalizedTitle = normalizeRequiredText(
                "title",
                title,
                MAX_TITLE_LENGTH,
                "자기소개서 제목은 필수입니다.",
                "자기소개서 제목은 최대 50자까지 입력할 수 있습니다.",
                details
        );
        String normalizedCompanyName = normalizeRequiredText(
                "companyName",
                companyName,
                MAX_COMPANY_NAME_LENGTH,
                "회사명은 필수입니다.",
                "회사명은 최대 30자까지 입력할 수 있습니다.",
                details
        );
        String normalizedPositionTitle = normalizeRequiredText(
                "positionTitle",
                positionTitle,
                MAX_POSITION_TITLE_LENGTH,
                "직무명은 필수입니다.",
                "직무명은 최대 30자까지 입력할 수 있습니다.",
                details
        );
        String normalizedJobPostingUrl = normalizeOptionalJobPostingUrl(jobPostingUrl, details);

        if (!details.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, details);
        }

        return new BasicInfoInput(
                normalizedTitle,
                normalizedCompanyName,
                normalizedPositionTitle,
                normalizedJobPostingUrl
        );
    }

    private String normalizeRequiredText(
            String field,
            String value,
            int maxLength,
            String blankMessage,
            String tooLongMessage,
            List<ErrorResponse.ErrorDetail> details
    ) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isEmpty()) {
            details.add(new ErrorResponse.ErrorDetail(field, blankMessage));
            return normalized;
        }
        if (countCodePoints(normalized) > maxLength) {
            details.add(new ErrorResponse.ErrorDetail(field, tooLongMessage));
        }
        return normalized;
    }

    private String normalizeOptionalJobPostingUrl(
            String value,
            List<ErrorResponse.ErrorDetail> details
    ) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if (countCodePoints(normalized) > MAX_JOB_POSTING_URL_LENGTH) {
            details.add(new ErrorResponse.ErrorDetail(
                    "jobPostingUrl",
                    "공고 링크는 최대 500자까지 입력할 수 있습니다."
            ));
        }
        if (!isAbsoluteUrl(normalized)) {
            details.add(new ErrorResponse.ErrorDetail(
                    "jobPostingUrl",
                    "공고 링크 형식이 올바르지 않습니다."
            ));
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.strip();
    }

    private int countCodePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    private boolean isAbsoluteUrl(String value) {
        try {
            URI uri = new URI(value);
            return uri.isAbsolute() && uri.getScheme() != null && !uri.getScheme().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private record BasicInfoInput(
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl
    ) {
    }
}
