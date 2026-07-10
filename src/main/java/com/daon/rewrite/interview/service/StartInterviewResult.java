package com.daon.rewrite.interview.service;

import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.llmjob.entity.LlmJob;

public record StartInterviewResult(
        InterviewSession interviewSession,
        LlmJob job
) {
}
