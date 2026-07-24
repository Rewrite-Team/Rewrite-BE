package com.daon.rewrite.interview.controller;

import com.daon.rewrite.interview.dto.AddInterviewQuestionResponse;
import com.daon.rewrite.interview.dto.CurrentInterviewResponse;
import com.daon.rewrite.interview.dto.InterviewQuestionListResponse;
import com.daon.rewrite.interview.dto.StartInterviewResponse;
import com.daon.rewrite.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public InterviewQuestionListResponse getInterviewQuestions(
            @PathVariable String interviewSessionId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        return InterviewQuestionListResponse.from(
                interviewService.findMyInterviewQuestions(interviewSessionId, cursor, size)
        );
    }

    @PostMapping("/cover-letters/{coverLetterId}/interviews")
    public StartInterviewResponse startInterview(@PathVariable String coverLetterId) {
        return StartInterviewResponse.from(interviewService.startMyInterview(coverLetterId));
    }

    @PostMapping("/interviews/{interviewSessionId}/questions")
    public AddInterviewQuestionResponse addInterviewQuestion(@PathVariable String interviewSessionId) {
        return AddInterviewQuestionResponse.from(
                interviewService.addMyInterviewQuestion(interviewSessionId)
        );
    }
}
