package com.daon.rewrite.llmjob.service;

import com.daon.rewrite.llmjob.dto.InterviewFeedbackDeltaResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 검증을 마친 면접 피드백을 화면 표시용 조각으로 나누고 Job별 재연결 자료로 보관한다.
 * 프로세스 메모리에만 보관하며, 호출자가 Job 종료를 확인하면 제거한다.
 */
final class InterviewFeedbackDeltaBuffer {

    private static final int CHUNK_SIZE = 40;

    private final Map<String, List<InterviewFeedbackDeltaResponse>> deltasByJobId = new ConcurrentHashMap<>();

    /** content를 조각으로 나누어 Job의 재연결 자료로 저장하고, 새로 전송할 조각을 반환한다. */
    List<InterviewFeedbackDeltaResponse> store(String jobId, String content) {
        List<InterviewFeedbackDeltaResponse> deltas = split(content);
        deltasByJobId.put(jobId, new CopyOnWriteArrayList<>(deltas));
        return deltas;
    }

    List<InterviewFeedbackDeltaResponse> find(String jobId) {
        return deltasByJobId.getOrDefault(jobId, List.of());
    }

    Set<String> jobIds() {
        return deltasByJobId.keySet();
    }

    void remove(String jobId) {
        deltasByJobId.remove(jobId);
    }

    /** code point 단위로 나눠 surrogate pair가 조각 경계에서 분리되지 않게 한다. sequence는 Job 안에서 1부터 시작한다. */
    private List<InterviewFeedbackDeltaResponse> split(String content) {
        int[] codePoints = content.codePoints().toArray();
        List<InterviewFeedbackDeltaResponse> deltas = new ArrayList<>();
        for (int start = 0, sequence = 1; start < codePoints.length; start += CHUNK_SIZE, sequence++) {
            int length = Math.min(CHUNK_SIZE, codePoints.length - start);
            deltas.add(new InterviewFeedbackDeltaResponse(
                    sequence,
                    new String(codePoints, start, length)
            ));
        }
        return List.copyOf(deltas);
    }
}
