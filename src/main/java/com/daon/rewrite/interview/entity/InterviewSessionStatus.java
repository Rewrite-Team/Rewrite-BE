package com.daon.rewrite.interview.entity;

/**
 * 초기 질문 생성의 생명주기다. 초기 실패는 FAILED에서 재시도하고 질문·thread 생성이 완료되면 ACTIVE가 된다.
 * 추가 질문 생성이나 답변 피드백 Job이 실패해도 기존 ACTIVE 세션은 유지한다.
 */
public enum InterviewSessionStatus {
    QUESTION_GENERATING,
    ACTIVE,
    FAILED
}
