package com.daon.rewrite.interview.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum InterviewMessageRole {
    USER,
    ASSISTANT
}
