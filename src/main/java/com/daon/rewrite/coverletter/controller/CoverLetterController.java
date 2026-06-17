package com.daon.rewrite.coverletter.controller;

import com.daon.rewrite.coverletter.dto.CoverLetterListResponse;
import com.daon.rewrite.coverletter.dto.CreateCoverLetterResponse;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CoverLetterController {

    private final CoverLetterService coverLetterService;

    @PostMapping("/cover-letters")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateCoverLetterResponse createDraft() {
        return CreateCoverLetterResponse.from(coverLetterService.createDraft());
    }

    @GetMapping("/cover-letters")
    public CoverLetterListResponse findMyCoverLetters(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(required = false) CoverLetterStatus status
    ) {
        Page<CoverLetter> result = coverLetterService.findMyCoverLetters(page, size, status);
        return CoverLetterListResponse.from(result, page, size);
    }
}
