package com.daon.rewrite.interview.controller;

import com.daon.rewrite.interview.dto.StartInterviewRequest;
import com.daon.rewrite.interview.dto.StartInterviewResponse;
import com.daon.rewrite.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/cover-letters/{coverLetterId}/interviews")
    public StartInterviewResponse startInterview(
            @PathVariable String coverLetterId,
            @RequestBody(required = false) StartInterviewRequest request
    ) {
        return StartInterviewResponse.from(interviewService.startMyInterview(
                coverLetterId,
                request == null ? null : request.sourceReviewVersionId()
        ));
    }
}
