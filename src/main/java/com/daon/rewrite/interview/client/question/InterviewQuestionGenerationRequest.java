package com.daon.rewrite.interview.client.question;

import java.util.List;

/**
 * 지원 정보와 생성 기준 버전의 전체 문항별 finalAnswer를 함께 전달하는 LLM 입력이다.
 * Job 계층이 초기 5개·추가 1개의 questionCount와 기존 면접 질문을 준비하며 client는 이 입력을 그대로 사용한다.
 */
public record InterviewQuestionGenerationRequest(
        String companyName,
        String positionTitle,
        String preferences,
        int questionCount,
        List<String> existingQuestions,
        List<InterviewQuestionGenerationAnswer> answers
) {

    public InterviewQuestionGenerationRequest {
        existingQuestions = List.copyOf(existingQuestions);
        answers = List.copyOf(answers);
    }
}
