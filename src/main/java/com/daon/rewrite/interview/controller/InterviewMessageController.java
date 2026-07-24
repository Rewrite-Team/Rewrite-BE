package com.daon.rewrite.interview.controller;

import com.daon.rewrite.interview.dto.InterviewMessageListResponse;
import com.daon.rewrite.interview.dto.SendInterviewMessageRequest;
import com.daon.rewrite.interview.dto.SendInterviewMessageResponse;
import com.daon.rewrite.interview.service.InterviewMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewMessageController {

    private final InterviewMessageService interviewMessageService;

    @GetMapping("/interview-threads/{threadId}/messages")
    public InterviewMessageListResponse getInterviewMessages(@PathVariable String threadId) {
        return InterviewMessageListResponse.from(
                interviewMessageService.findMyInterviewMessages(threadId)
        );
    }

    @PostMapping("/interview-threads/{threadId}/messages")
    public SendInterviewMessageResponse sendInterviewMessage(
            @PathVariable String threadId,
            @RequestBody(required = false) SendInterviewMessageRequest request
    ) {
        return SendInterviewMessageResponse.from(
                interviewMessageService.sendMyInterviewMessage(
                        threadId,
                        request == null ? null : request.content()
                )
        );
    }
}
