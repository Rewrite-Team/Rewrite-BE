package com.daon.rewrite.llmjob.service;

import com.daon.rewrite.global.sse.BufferedSseConnection;
import com.daon.rewrite.llmjob.dto.LlmJobStateEventResponse;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 한 Job 구독 연결의 SSE 전송 수단과 전송 이력을 보관한다.
 * 초기 스냅샷과 동시 발행의 중복을 걸러내며, 재연결하면 새 이력으로 시작한다.
 */
final class JobStreamConnection {

    private final BufferedSseConnection stream;
    private final Set<String> seenQuestionIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> seenDeltaSequences = ConcurrentHashMap.newKeySet();
    private volatile LlmJobStateEventResponse lastState;

    JobStreamConnection(BufferedSseConnection stream) {
        this.stream = stream;
    }

    BufferedSseConnection stream() {
        return stream;
    }

    Set<String> seenQuestionIds() {
        return seenQuestionIds;
    }

    Set<Integer> seenDeltaSequences() {
        return seenDeltaSequences;
    }

    LlmJobStateEventResponse lastState() {
        return lastState;
    }

    void lastState(LlmJobStateEventResponse lastState) {
        this.lastState = lastState;
    }
}
