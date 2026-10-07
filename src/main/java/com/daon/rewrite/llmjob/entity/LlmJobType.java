package com.daon.rewrite.llmjob.entity;

/**
 * 같은 Job 상태 모델로 처리하는 도메인 작업의 종류다.
 * 이벤트 리스너의 worker 선택과 각 도메인의 중복 요청 재사용·충돌 판단에 사용한다.
 */
public enum LlmJobType {
    COVER_LETTER_REVIEW,
    COVER_LETTER_RE_REVIEW,
    KEYWORD_ANALYSIS,
    INTERVIEW_INITIAL_QUESTION_GENERATION,
    INTERVIEW_ADDITIONAL_QUESTION_GENERATION,
    INTERVIEW_MESSAGE_FEEDBACK;

    public boolean isInterviewQuestionGeneration() {
        return this == INTERVIEW_INITIAL_QUESTION_GENERATION
                || this == INTERVIEW_ADDITIONAL_QUESTION_GENERATION;
    }
}
