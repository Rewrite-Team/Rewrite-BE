package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CoverLetterService {

    private static final String COVER_LETTER_ID_PREFIX = "cl";
    private static final int MAX_LIST_SIZE = 9;

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

    private void validateListQuery(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_LIST_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
