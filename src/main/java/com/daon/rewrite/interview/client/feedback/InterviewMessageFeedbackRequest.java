package com.daon.rewrite.interview.client.feedback;

import java.util.List;

/**
 * 원본 면접 질문과 평가할 USER 답변까지의 시간순 role·content 이력을 전달한다.
 * 원본 질문은 메시지와 별도로 제공하고 이전 ASSISTANT의 content에는 피드백과 꼬리질문이 함께 담긴다.
 * client는 메시지를 조회·정렬하지 않고 Job 계층이 준비한 이력을 그대로 사용한다.
 */
public record InterviewMessageFeedbackRequest(
        String originalQuestion,
        List<InterviewMessageFeedbackMessage> messages
) {

    public InterviewMessageFeedbackRequest {
        messages = List.copyOf(messages);
    }
}
