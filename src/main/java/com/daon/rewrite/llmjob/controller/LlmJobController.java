package com.daon.rewrite.llmjob.controller;

import com.daon.rewrite.llmjob.dto.LlmJobStateResponse;
import com.daon.rewrite.llmjob.service.LlmJobService;
import com.daon.rewrite.llmjob.service.LlmJobStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class LlmJobController {

    private final LlmJobService llmJobService;
    private final LlmJobStreamService llmJobStreamService;

    @GetMapping("/llm-jobs/{jobId}")
    public LlmJobStateResponse findJob(@PathVariable String jobId) {
        return LlmJobStateResponse.from(llmJobService.findMyJob(jobId));
    }

    @GetMapping(path = "/llm-jobs/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJob(@PathVariable String jobId) {
        return llmJobStreamService.openMyJobStream(jobId);
    }
}
