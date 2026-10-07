package com.daon.rewrite.interview.entity;

/**
 * 기존 persistence 호환을 위해 유지하는 내부 질문 분류다.
 * 신규 질문은 모두 COVER_LETTER_BASED로 저장하며 LLM 출력과 공개 질문 목록에는 유형 구분을 사용하지 않는다.
 */
public enum InterviewQuestionType {
    COVER_LETTER_BASED,
    TECHNICAL
}
