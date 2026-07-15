package com.daon.rewrite.interview.controller;

import com.daon.rewrite.interview.dto.AddInterviewQuestionResponse;
import com.daon.rewrite.interview.dto.CurrentInterviewResponse;
import com.daon.rewrite.interview.dto.InterviewQuestionListResponse;
import com.daon.rewrite.interview.dto.StartInterviewRequest;
import com.daon.rewrite.interview.dto.StartInterviewResponse;
import com.daon.rewrite.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping("/cover-letters/{coverLetterId}/interview")
    public CurrentInterviewResponse getCurrentInterview(@PathVariable String coverLetterId) {
        return CurrentInterviewResponse.from(interviewService.findMyCurrentInterview(coverLetterId));
    }

    @GetMapping("/interviews/{interviewSessionId}/questions")
    public InterviewQuestionListResponse getInterviewQuestions(@PathVariable String interviewSessionId) {
        return InterviewQuestionListResponse.from(
                interviewService.findMyInterviewQuestions(interviewSessionId)
        );
    }

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

    @PostMapping("/interviews/{interviewSessionId}/questions")
    public AddInterviewQuestionResponse addInterviewQuestion(@PathVariable String interviewSessionId) {
        return AddInterviewQuestionResponse.from(
                interviewService.addMyInterviewQuestion(interviewSessionId)
        );
    }
}
