package com.daon.rewrite.llmjob.entity;

/**
 * 비동기 작업의 실행 상태다. PENDING·PROCESSING은 같은 자기소개서의 새 Job을 배제하는 진행 중 상태다.
 * 완료·실패·취소는 상태 조회와 SSE에서 구분하는 종료 상태다.
 */
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
