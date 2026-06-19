package com.daon.rewrite.llmjob.controller;

import com.daon.rewrite.llmjob.dto.LlmJobResponse;
import com.daon.rewrite.llmjob.service.LlmJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LlmJobController {

    private final LlmJobService llmJobService;

    @GetMapping("/llm-jobs/{jobId}")
    public LlmJobResponse findJob(@PathVariable String jobId) {
        return LlmJobResponse.from(llmJobService.findMyJob(jobId));
    }
}
