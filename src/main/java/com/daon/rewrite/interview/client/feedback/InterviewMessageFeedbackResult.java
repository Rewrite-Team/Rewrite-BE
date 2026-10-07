package com.daon.rewrite.interview.client.feedback;

import java.util.List;

/**
 * content는 LLM이 피드백과 꼬리질문을 연결해 생성한 전체 표시 문장이고 score는 공개하는 보조 점수다.
 * 구조화된 피드백과 followUpQuestion은 같은 assistant 메시지에 내부 저장한다.
 * client가 이 필드들로 content를 다시 조합하지 않으며 다음 요청의 대화 이력에는 저장된 content를 사용한다.
 */
public record InterviewMessageFeedbackResult(
        String content,
        String feedbackSummary,
        List<String> feedbackStrengths,
        List<String> feedbackImprovements,
        int score,
        String followUpQuestion
) {

    public InterviewMessageFeedbackResult {
        feedbackStrengths = List.copyOf(feedbackStrengths);
        feedbackImprovements = List.copyOf(feedbackImprovements);
    }
}
