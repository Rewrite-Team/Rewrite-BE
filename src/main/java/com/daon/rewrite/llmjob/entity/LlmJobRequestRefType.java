package com.daon.rewrite.llmjob.entity;

/**
 * Job 생성 시 확정한 입력 리소스의 종류다.
 * 재첨삭·면접 질문 생성의 기준 버전과 면접 피드백의 USER 메시지를 requestRefId와 함께 식별한다.
 */
public enum LlmJobRequestRefType {
    REVIEW_VERSION,
    INTERVIEW_MESSAGE
}
