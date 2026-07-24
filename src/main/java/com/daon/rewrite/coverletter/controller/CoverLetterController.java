package com.daon.rewrite.coverletter.controller;

import com.daon.rewrite.coverletter.dto.CoverLetterListResponse;
import com.daon.rewrite.coverletter.dto.CoverLetterDetailResponse;
import com.daon.rewrite.coverletter.dto.CreateCoverLetterResponse;
import com.daon.rewrite.coverletter.dto.SaveBasicInfoRequest;
import com.daon.rewrite.coverletter.dto.SavePreferencesRequest;
import com.daon.rewrite.coverletter.dto.SaveQuestionsRequest;
import com.daon.rewrite.coverletter.dto.SubmitCoverLetterResponse;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.service.CoverLetterService;
import com.daon.rewrite.coverletter.service.CoverLetterReviewStatusStreamService;
import com.daon.rewrite.coverletter.service.CoverLetterDetailQueryService;
import com.daon.rewrite.coverletter.service.SubmitCoverLetterResult;
import com.daon.rewrite.global.response.SuccessResponse;
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
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class CoverLetterController {

    private final CoverLetterService coverLetterService;
    private final CoverLetterReviewStatusStreamService coverLetterReviewStatusStreamService;
    private final CoverLetterDetailQueryService coverLetterDetailQueryService;

    @PostMapping("/cover-letters")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateCoverLetterResponse create() {
        return CreateCoverLetterResponse.from(coverLetterService.create());
    }

    /**
     * 현재 사용자의 자기소개서 목록을 조회
     *
     * @param page 1부터 시작하는 페이지 번호. 생략하면 1
     * @param size 페이지당 항목 수. 생략하면 9
     * @return 자기소개서 목록과 페이지 정보
     */
    @GetMapping("/cover-letters")
    public CoverLetterListResponse findMyCoverLetters(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size
    ) {
        Page<CoverLetter> result = coverLetterService.findMyCoverLetters(page, size);
        return CoverLetterListResponse.from(result, page, size);
    }

    @GetMapping("/cover-letters/{coverLetterId}")
    public CoverLetterDetailResponse findMyCoverLetter(@PathVariable String coverLetterId) {
        return CoverLetterDetailResponse.from(coverLetterDetailQueryService.findCurrent(coverLetterId));
    }

    @DeleteMapping("/cover-letters/{coverLetterId}")
    public SuccessResponse deleteMyCoverLetter(@PathVariable String coverLetterId) {
        coverLetterService.deleteMyCoverLetter(coverLetterId);
        return SuccessResponse.completed();
    }

    @PutMapping("/cover-letters/{coverLetterId}/basic-info")
    public SuccessResponse saveBasicInfo(
            @PathVariable String coverLetterId,
            @RequestBody SaveBasicInfoRequest request
    ) {
        coverLetterService.saveBasicInfo(
                coverLetterId,
                request.title(),
                request.companyName(),
                request.positionTitle(),
                request.jobPostingUrl()
        );
        return SuccessResponse.completed();
    }

    @PutMapping("/cover-letters/{coverLetterId}/preferences")
    public SuccessResponse savePreferences(
            @PathVariable String coverLetterId,
            @RequestBody SavePreferencesRequest request
    ) {
        coverLetterService.savePreferences(
                coverLetterId,
                request.preferences()
        );
        return SuccessResponse.completed();
    }

    @PutMapping("/cover-letters/{coverLetterId}/questions")
    public SuccessResponse saveQuestions(
            @PathVariable String coverLetterId,
            @RequestBody SaveQuestionsRequest request
    ) {
        coverLetterService.saveQuestions(
                coverLetterId,
                request.toInputs()
        );
        return SuccessResponse.completed();
    }

    @PostMapping("/cover-letters/{coverLetterId}/submit")
    public SubmitCoverLetterResponse submit(@PathVariable String coverLetterId) {
        SubmitCoverLetterResult result = coverLetterService.submit(coverLetterId);
        return SubmitCoverLetterResponse.from(result);
    }

    @GetMapping(path = "/cover-letters/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCoverLetterReviewStatuses() {
        return coverLetterReviewStatusStreamService.openMyStream();
    }
}
