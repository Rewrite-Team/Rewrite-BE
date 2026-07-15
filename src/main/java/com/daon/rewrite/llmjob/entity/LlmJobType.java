package com.daon.rewrite.llmjob.entity;

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
