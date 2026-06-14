package com.daon.rewrite.coverletter.controller;

import com.daon.rewrite.coverletter.dto.CreateCoverLetterResponse;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
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
}
