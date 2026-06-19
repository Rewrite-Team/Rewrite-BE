package com.daon.rewrite.coverletter.controller;

import com.daon.rewrite.coverletter.dto.CoverLetterListResponse;
import com.daon.rewrite.coverletter.dto.CreateCoverLetterResponse;
import com.daon.rewrite.coverletter.dto.DeleteCoverLetterResponse;
import com.daon.rewrite.coverletter.dto.SaveBasicInfoRequest;
import com.daon.rewrite.coverletter.dto.SaveBasicInfoResponse;
import com.daon.rewrite.coverletter.dto.SavePreferencesRequest;
import com.daon.rewrite.coverletter.dto.SavePreferencesResponse;
import com.daon.rewrite.coverletter.dto.SaveQuestionsRequest;
import com.daon.rewrite.coverletter.dto.SaveQuestionsResponse;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.coverletter.service.SaveQuestionsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @DeleteMapping("/cover-letters/{coverLetterId}")
    public DeleteCoverLetterResponse deleteMyCoverLetter(@PathVariable String coverLetterId) {
        return DeleteCoverLetterResponse.from(coverLetterService.deleteMyCoverLetter(coverLetterId));
    }

    @PutMapping("/cover-letters/{coverLetterId}/basic-info")
    public SaveBasicInfoResponse saveBasicInfo(
            @PathVariable String coverLetterId,
            @RequestBody SaveBasicInfoRequest request
    ) {
        CoverLetter result = coverLetterService.saveBasicInfo(
                coverLetterId,
                request.title(),
                request.companyName(),
                request.positionTitle(),
                request.jobPostingUrl()
        );
        return SaveBasicInfoResponse.from(result);
    }

    @PutMapping("/cover-letters/{coverLetterId}/preferences")
    public SavePreferencesResponse savePreferences(
            @PathVariable String coverLetterId,
            @RequestBody SavePreferencesRequest request
    ) {
        CoverLetter result = coverLetterService.savePreferences(
                coverLetterId,
                request.preferences()
        );
        return SavePreferencesResponse.from(result);
    }

    @PutMapping("/cover-letters/{coverLetterId}/questions")
    public SaveQuestionsResponse saveQuestions(
            @PathVariable String coverLetterId,
            @RequestBody SaveQuestionsRequest request
    ) {
        SaveQuestionsResult result = coverLetterService.saveQuestions(
                coverLetterId,
                request.toInputs()
        );
        return SaveQuestionsResponse.from(result);
    }
}
