package com.daon.rewrite.interview.service;

import com.daon.rewrite.interview.entity.InterviewMessageRole;

import java.time.Instant;

public record InterviewMessageItemResult(
        String id,
        InterviewMessageRole role,
        String content,
        Integer score,
        Instant createdAt
) {
}
