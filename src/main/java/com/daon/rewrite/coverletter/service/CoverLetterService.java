package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CoverLetterService {

    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterIdGenerator coverLetterIdGenerator;
    private final Clock clock;

    public CoverLetter createDraft() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter draft = CoverLetter.draft(
                coverLetterIdGenerator.generate(),
                currentUser.id(),
                LocalDateTime.now(clock)
        );

        return coverLetterRepository.save(draft);
    }
}
