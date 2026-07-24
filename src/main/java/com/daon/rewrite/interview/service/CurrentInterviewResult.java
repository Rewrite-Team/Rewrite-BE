package com.daon.rewrite.interview.service;

import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.interview.entity.InterviewSession;
import com.daon.rewrite.llmjob.entity.LlmJob;

public record CurrentInterviewResult(
        CoverLetter coverLetter,
        InterviewSession interviewSession,
        LlmJob job
) {
}
