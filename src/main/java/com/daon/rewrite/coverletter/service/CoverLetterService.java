package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CoverLetterService {

    private static final String COVER_LETTER_ID_PREFIX = "cl";

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
}
