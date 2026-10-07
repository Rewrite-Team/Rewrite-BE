package com.daon.rewrite.reviewversion.client;

/**
 * LLM에 전달할 문항 입력이며 originalAnswer는 이번 첨삭의 실제 입력 답변이다.
 * 재첨삭에서는 제출 원본 대신 요청 시점 최신 성공 버전의 finalAnswer가 들어간다.
 */
public record ReviewQuestion(
        String questionId,
        int questionOrder,
        String question,
        int maxAnswerLength,
        String originalAnswer
) {
}
