package com.daon.rewrite.interview.service;

import com.daon.rewrite.interview.entity.InterviewSession;

public record CurrentInterviewResult(
        String coverLetterId,
        InterviewSession interviewSession
) {
}
