package com.daon.rewrite.llmjob.entity;

/**
 * Job 완료 후 결과를 조회할 도메인 리소스의 종류다.
 * resultRefId와 함께 최종 결과를 가리키며, 작업 입력을 선택하는 requestRef와는 별도로 기록한다.
 */
public enum LlmJobResultRefType {
    REVIEW_VERSION,
    KEYWORD_ANALYSIS,
    INTERVIEW_SESSION,
    INTERVIEW_QUESTION,
    INTERVIEW_MESSAGE
}
