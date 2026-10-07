package com.daon.rewrite.llmjob.dto;

/** 검증된 면접 피드백 문장을 분할한 조각. 클라이언트는 sequence 순서로 contentDelta를 이어 붙인다. */
public record InterviewFeedbackDeltaResponse(int sequence, String contentDelta) {
}
