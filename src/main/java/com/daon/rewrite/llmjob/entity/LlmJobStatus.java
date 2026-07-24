package com.daon.rewrite.llmjob.entity;

public enum LlmJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED;
    }
}
